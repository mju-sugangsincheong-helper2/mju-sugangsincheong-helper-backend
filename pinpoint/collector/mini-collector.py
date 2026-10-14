# /// script
# requires-python = ">=3.10"
# dependencies = ["grpcio", "protobuf", "grpcio-tools"]
# ///
"""Pinpoint mini-collector (dev 전용, 임시).

collector/HBase 없이 agent가 쏘는 데이터를 전부 JSONL로 저장한다.
  - 9991: Agent (agent info, ping) + Metadata (sql/api/string 메타)
  - 9992: Stat (JVM 스탯)
  - 9993: Span (트레이스)

실행: uv run pinpoint/collector/mini-collector.py
출력: pinpoint/collector/mini-collector-data/*.jsonl
"""

import json
import sys
import threading
import urllib.request
from concurrent import futures
from datetime import datetime, timezone
from pathlib import Path

BASE = Path(__file__).resolve().parent
CACHE = BASE / ".mini-collector-cache"
PROTO_ROOT = CACHE / "proto"          # <root>/v1/*.proto
GEN_ROOT = CACHE / "gen"              # protoc 산출물
DATA = BASE / "mini-collector-data"

IDL_BASE_URL = "https://raw.githubusercontent.com/pinpoint-apm/pinpoint-grpc-idl/master/proto/v1"
PROTO_FILES = ["Annotation.proto", "Cmd.proto", "Log.proto", "Service.proto",
               "Span.proto", "Stat.proto", "ThreadDump.proto", "CustomMetric.proto"]

PORTS = {"agent+metadata": 9991, "stat": 9992, "span": 9993}


def ensure_protos() -> None:
    dest = PROTO_ROOT / "v1"
    dest.mkdir(parents=True, exist_ok=True)
    for name in PROTO_FILES:
        f = dest / name
        if f.exists() and f.stat().st_size > 0:
            continue
        print(f"[bootstrap] download {name}", flush=True)
        urllib.request.urlretrieve(f"{IDL_BASE_URL}/{name}", f)


def ensure_stubs() -> None:
    import os

    import grpc_tools
    from grpc_tools import protoc

    well_known = os.path.join(os.path.dirname(grpc_tools.__file__), "_proto")

    GEN_ROOT.mkdir(parents=True, exist_ok=True)
    marker = GEN_ROOT / ".generated"
    if marker.exists():
        return
    print("[bootstrap] protoc generate", flush=True)
    args = ["protoc", f"-I{PROTO_ROOT}", f"-I{well_known}",
            f"--python_out={GEN_ROOT}", f"--grpc_python_out={GEN_ROOT}"]
    args += [f"v1/{n}" for n in PROTO_FILES]
    if protoc.main(args) != 0:
        raise RuntimeError("protoc failed")
    (GEN_ROOT / "v1" / "__init__.py").touch(exist_ok=True)
    marker.touch()


ensure_protos()
ensure_stubs()
sys.path.insert(0, str(GEN_ROOT))

import grpc  # noqa: E402
from google.protobuf import empty_pb2  # noqa: E402
from google.protobuf.json_format import MessageToDict  # noqa: E402

from v1 import Service_pb2 as svc  # noqa: E402
from v1 import Service_pb2_grpc as svc_grpc  # noqa: E402
from v1 import Span_pb2 as span_pb2  # noqa: E402

DATA.mkdir(parents=True, exist_ok=True)
_lock = threading.Lock()
_files: dict[str, object] = {}


def append_jsonl(stream: str, method: str, msg) -> None:
    with _lock:
        fh = _files.get(stream)
        if fh is None:
            fh = open(DATA / f"{stream}.jsonl", "a", encoding="utf-8")
            _files[stream] = fh
        fh.write(json.dumps({"ts": datetime.now(timezone.utc).isoformat(),
                             "method": method,
                             "msg": MessageToDict(msg)},
                            ensure_ascii=False) + "\n")
        fh.flush()


def ok():
    return span_pb2.PResult(success=True)


class Agent(svc_grpc.AgentServicer):
    def RequestAgentInfo(self, request, context):
        append_jsonl("agent", "RequestAgentInfo", request)
        print(f"[agent] info agentId={request.agentId} app={request.applicationName} "
              f"serverType={request.serverType}", flush=True)
        return ok()

    def PingSession(self, request_iterator, context):
        for ping in request_iterator:  # echo = pong
            yield ping


class Metadata(svc_grpc.MetadataServicer):
    def _handle(self, method, request):
        append_jsonl("metadata", method, request)
        d = MessageToDict(request)
        print(f"[metadata] {method} keys={list(d)[:4]}", flush=True)
        return ok()

    def RequestSqlMetaData(self, request, context):
        return self._handle("RequestSqlMetaData", request)

    def RequestSqlUidMetaData(self, request, context):
        return self._handle("RequestSqlUidMetaData", request)

    def RequestApiMetaData(self, request, context):
        d = MessageToDict(request)
        print(f"[metadata] RequestApiMetaData apiId={d.get('apiId')} "
              f"line={str(d.get('line', ''))[:80]}", flush=True)
        append_jsonl("metadata", "RequestApiMetaData", request)
        return ok()

    def RequestStringMetaData(self, request, context):
        return self._handle("RequestStringMetaData", request)

    def RequestExceptionMetaData(self, request, context):
        return self._handle("RequestExceptionMetaData", request)


class ProfilerCommandService(svc_grpc.ProfilerCommandServiceServicer):
    """원격 명령(스레드덤프 등) 미지원 스텁.

    에이전트는起動 시 ProfilerCommandService.HandleCommand[V2] 양방향 스트림을 열어
    커맨드 채널을 유지하려 한다. 미니컬렉터는 명령을 보낼 일이 없으므로 요청을
    소비만 하고 응답은 보내지 않는다. 스트림이 열려 있으면 에이전트가 3초마다
    재접속(UNIMPLEMENTED 스팸)하지 않는다.
    """

    def _hold(self, request_iterator):
        try:
            for _ in request_iterator:
                pass
        except Exception:
            pass
        return iter(())

    def HandleCommand(self, request_iterator, context):
        return self._hold(request_iterator)

    def HandleCommandV2(self, request_iterator, context):
        return self._hold(request_iterator)

    def CommandEcho(self, request, context):
        return empty_pb2.Empty()


class Stat(svc_grpc.StatServicer):
    def SendAgentStat(self, request_iterator, context):
        n = 0
        for stat in request_iterator:
            append_jsonl("stat", "SendAgentStat", stat)
            n += 1
        print(f"[stat] batch messages={n}", flush=True)
        return empty_pb2.Empty()


class Span(svc_grpc.SpanServicer):
    def SendSpan(self, request_iterator, context):
        for msg in request_iterator:
            self._log(msg)
        return empty_pb2.Empty()

    def SendSpanBatch(self, request, context):
        print(f"[span] batch size={request.spanCount if hasattr(request, 'spanCount') else len(request.span)}",
              flush=True)
        for msg in request.span:
            self._log(msg)
        return span_pb2.PSpanResultBatch()

    def _log(self, msg):
        append_jsonl("spans", "SendSpan", msg)
        field = msg.WhichOneof("field")
        if field == "span":
            s = msg.span
            rpc = s.acceptEvent.rpc if s.HasField("acceptEvent") else "-"
            tx = s.transactionId
            print(f"[span] tx={tx.agentId}^{tx.sequence} spanId={s.spanId} "
                  f"elapsed={s.elapsed}ms rpc={rpc} events={len(s.spanEvent)}", flush=True)
        else:
            print(f"[span] chunk events={len(msg.spanChunk.spanEvent)}", flush=True)


def main() -> None:
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=16))
    svc_grpc.add_AgentServicer_to_server(Agent(), server)
    svc_grpc.add_MetadataServicer_to_server(Metadata(), server)
    svc_grpc.add_StatServicer_to_server(Stat(), server)
    svc_grpc.add_SpanServicer_to_server(Span(), server)
    svc_grpc.add_ProfilerCommandServiceServicer_to_server(ProfilerCommandService(), server)
    for name, port in PORTS.items():
        server.add_insecure_port(f"[::]:{port}")
        print(f"[listen] {name} -> {port}", flush=True)
    print(f"[data] {DATA}", flush=True)
    server.start()
    server.wait_for_termination()


if __name__ == "__main__":
    main()
