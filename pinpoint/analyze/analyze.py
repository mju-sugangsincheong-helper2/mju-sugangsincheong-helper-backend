#!/usr/bin/env python3
# /// script
# requires-python = ">=3.10"
# dependencies = []
# ///
"""Pinpoint mini-collector JSONL -> 단일 요청 CallStack JSON.

계층: <action> 2개 — list(전체 목록), show(1건 상세).
show 옵션: 조건(어떤 요청을) / 출력 형태(tree/json/yaml) /
상세도·직렬화·위치(얼마나·어떻게 찍나·어디에).

실행: uv run pinpoint/analyze/analyze.py <action> [options]

    uv run pinpoint/analyze/analyze.py list --sort elapsed --limit 5
    uv run pinpoint/analyze/analyze.py show --tx 42
    uv run pinpoint/analyze/analyze.py show --tx 42 --name full
    uv run pinpoint/analyze/analyze.py show --rpc orders --status 200 --pick latest
    uv run pinpoint/analyze/analyze.py show --tx 42 --format yaml

Pinpoint Web의 CallTree와 동일한 규칙을 단순화해 재현한다.
(단일 노드/단일 spanId 케이스만 지원 — 분산 링크/비동기 분기는 표시만)

참고한 원본 (pinpoint-apm/pinpoint@master):
- web/.../trace/span/Node.java              # PSpan + PSpanChunk 병합(mergeAndSort)
- web/.../trace/span/AlignComparator.java    # sequence 정렬 (SpanEventComparator)
- commons-server/.../bo/SpanEventBo.java     # depth 기본값 -1, sequence 기본값 0
- web/.../trace/span/SpanCallTree.java       # add(depth, align) 트리 빌드
  - MIN_DEPTH=-1, LEVEL_DEPTH=-1(형제), ROOT_DEPTH=0
  - depth==cursor → 형제, depth==cursor+1 → 자식, depth<cursor → 조상 탐색
  - hasCorrupted: cursor.seq+1 != next.seq → corrupted 노드
- web/.../trace/callstacks/RecordFactory.java # CallTreeNode -> Record 변환
- commons/.../trace/AnnotationKey.java        # 20=SQL-ID, 46=http.status 등
- commons/.../trace/ServiceType.java          # 1010 TOMCAT, 5051 MVC, 2500 PG 등

proto 기본값 주의 (MessageToDict가 0을 생략):
- sequence 누락 = 0 (root의 첫 spanEvent)
- depth 누락 = -1 (LEVEL_DEPTH = 형제 노드)
- startElapsed/endElapsed 누락 = 0 으로 취급하되 start/end 계산 시 보정
"""

from __future__ import annotations

import argparse
import json
import sys
from collections import defaultdict
from dataclasses import dataclass, field
from pathlib import Path

# mini-collector-data 기본 위치 (이 파일 기준 ../collector/mini-collector-data)
DEFAULT_DATA_DIR = Path(__file__).resolve().parent.parent / "collector" / "mini-collector-data"

LEVEL_DEPTH = -1
ROOT_DEPTH = 0

#: 잘라내기 기본값. --sql full 지정 시 None(원시값 전체)
DEFAULT_TRUNCATE = 300

#: 트리 한 줄의 argument 표시 길이. --sql full 지정 시 전체
TREE_ARG_LEN = 120

# ── 표준 모델 (공식 코드 기준, 단일 파일 유지를 위해 인라인)
# - AnnotationKey.java 전체: 코드+이름+VIEW_IN_RECORD_SET 여부
# - ServiceType.java 상단 javadoc 카탈로그 표 전체: 코드+이름
# (ServiceType 전체 레지스트리는 플러그인별로 분산돼 있어, javadoc 표가 공식 단일 출처)
@dataclass(frozen=True)
class AnnotationKeyDef:
    """AnnotationKey 1건. view_in_record=True만 Web CallStack에 표시된다."""
    code: int
    name: str
    view_in_record: bool


ANNOTATION_KEYS: dict[int, AnnotationKeyDef] = {
    -9999: AnnotationKeyDef(-9999, 'UNKNOWN', False),
    -100: AnnotationKeyDef(-100, 'Asynchronous Invocation', True),
    -52: AnnotationKeyDef(-52, 'ExceptionChainId', False),
    -51: AnnotationKeyDef(-51, 'ExceptionClass', False),
    -50: AnnotationKeyDef(-50, 'Exception', True),
    -40: AnnotationKeyDef(-40, 'cached_args[N]', False),
    -39: AnnotationKeyDef(-39, 'cached_args[9]', False),
    -38: AnnotationKeyDef(-38, 'cached_args[8]', False),
    -37: AnnotationKeyDef(-37, 'cached_args[7]', False),
    -36: AnnotationKeyDef(-36, 'cached_args[6]', False),
    -35: AnnotationKeyDef(-35, 'cached_args[5]', False),
    -34: AnnotationKeyDef(-34, 'cached_args[4]', False),
    -33: AnnotationKeyDef(-33, 'cached_args[3]', False),
    -32: AnnotationKeyDef(-32, 'cached_args[2]', False),
    -31: AnnotationKeyDef(-31, 'cached_args[1]', False),
    -30: AnnotationKeyDef(-30, 'cached_args[0]', False),
    -11: AnnotationKeyDef(-11, 'args[N]', False),
    -10: AnnotationKeyDef(-10, 'args[9]', False),
    -9: AnnotationKeyDef(-9, 'args[8]', False),
    -8: AnnotationKeyDef(-8, 'args[7]', False),
    -7: AnnotationKeyDef(-7, 'args[6]', False),
    -6: AnnotationKeyDef(-6, 'args[5]', False),
    -5: AnnotationKeyDef(-5, 'args[4]', False),
    -4: AnnotationKeyDef(-4, 'args[3]', False),
    -3: AnnotationKeyDef(-3, 'args[2]', False),
    -2: AnnotationKeyDef(-2, 'args[1]', False),
    -1: AnnotationKeyDef(-1, 'args[0]', False),
    12: AnnotationKeyDef(12, 'API', False),
    13: AnnotationKeyDef(13, 'API-METADATA', False),
    14: AnnotationKeyDef(14, 'RETURN_DATA', True),
    20: AnnotationKeyDef(20, 'SQL-ID', False),
    21: AnnotationKeyDef(21, 'SQL', True),
    22: AnnotationKeyDef(22, 'SQL-METADATA', False),
    23: AnnotationKeyDef(23, 'SQL-PARAM', False),
    24: AnnotationKeyDef(24, 'SQL-BindValue', True),
    25: AnnotationKeyDef(25, 'SQL-UID', False),
    30: AnnotationKeyDef(30, 'STRING_ID', False),
    40: AnnotationKeyDef(40, 'http.url', False),
    41: AnnotationKeyDef(41, 'http.param', True),
    42: AnnotationKeyDef(42, 'http.entity', True),
    45: AnnotationKeyDef(45, 'http.cookie', True),
    46: AnnotationKeyDef(46, 'http.status.code', True),
    47: AnnotationKeyDef(47, 'http.req.header', True),
    48: AnnotationKeyDef(48, 'http.internal.display', False),
    49: AnnotationKeyDef(49, 'http.io', True),
    50: AnnotationKeyDef(50, 'http.method', True),
    51: AnnotationKeyDef(51, 'http.secure', True),
    55: AnnotationKeyDef(55, 'http.resp.header', True),
    100: AnnotationKeyDef(100, 'message.queue.url', False),
    300: AnnotationKeyDef(300, 'PROXY_HTTP_HEADER', True),
    310: AnnotationKeyDef(310, 'redis.io', False),
    330: AnnotationKeyDef(330, 'memory.usage', True),
    400: AnnotationKeyDef(400, 'Attribute', True),
    402: AnnotationKeyDef(402, 'Event', True),
    403: AnnotationKeyDef(403, 'Link', True),
    404: AnnotationKeyDef(404, 'SpanId', False),
    405: AnnotationKeyDef(405, 'ParentSpanId', False),
    406: AnnotationKeyDef(406, 'Dropped', True),
    407: AnnotationKeyDef(407, 'Truncated', True),
    408: AnnotationKeyDef(408, 'Scope', False),
    409: AnnotationKeyDef(409, 'gen_ai.model', True),
    410: AnnotationKeyDef(410, 'gen_ai.usage', True),
    411: AnnotationKeyDef(411, 'gen_ai.ttft', True),
    10015: AnnotationKeyDef(10015, 'API-TAG', False),
    10000010: AnnotationKeyDef(10000010, 'API-METADATA-ERROR', False),
    10000011: AnnotationKeyDef(10000011, 'API-METADATA-AGENT-INFO-NOT-FOUND', False),
    10000012: AnnotationKeyDef(10000012, 'API-METADATA-IDENTIFIER-CHECK_ERROR', False),
    10000013: AnnotationKeyDef(10000013, 'API-METADATA-NOT-FOUND', False),
    10000014: AnnotationKeyDef(10000014, 'API-METADATA-DID-COLLSION', False),
}

# 자주 쓰는 키 (값은 규격이라 바꾸면 안 됨)
ANNOTATION_KEY_SQL_ID = 20  # SQL 식별자 -> 메타에서 SQL 본문 조회
ANNOTATION_KEY_HTTP_STATUS = 46  # 응답 HTTP 상태코드


@dataclass(frozen=True)
class ServiceTypeDef:
    code: int
    name: str


SERVICE_TYPES: dict[int, ServiceTypeDef] = {
    -1: ServiceTypeDef(-1, 'UNDEFINED'),
    1: ServiceTypeDef(1, 'UNKNOWN'),
    2: ServiceTypeDef(2, 'USER'),
    3: ServiceTypeDef(3, 'UNKNOWN_GROUP'),
    5: ServiceTypeDef(5, 'TEST'),
    7: ServiceTypeDef(7, 'COLLECTOR'),
    100: ServiceTypeDef(100, 'ASYNC'),
    500: ServiceTypeDef(500, 'SDK'),
    510: ServiceTypeDef(510, 'SDK_ASYNC'),
    1000: ServiceTypeDef(1000, 'STAND_ALONE'),
    1001: ServiceTypeDef(1001, 'JAVA'),
    1005: ServiceTypeDef(1005, 'TEST_STAND_ALONE'),
    1010: ServiceTypeDef(1010, 'TOMCAT'),
    1011: ServiceTypeDef(1011, 'TOMCAT_METHOD'),
    1030: ServiceTypeDef(1030, 'JETTY'),
    1031: ServiceTypeDef(1031, 'JETTY_METHOD'),
    1040: ServiceTypeDef(1040, 'JBOSS'),
    1041: ServiceTypeDef(1041, 'JBOSS_METHOD'),
    1050: ServiceTypeDef(1050, 'VERTX'),
    1051: ServiceTypeDef(1051, 'VERTX_INTERNAL'),
    1052: ServiceTypeDef(1052, 'VERTX_HTTP_SERVER'),
    1053: ServiceTypeDef(1053, 'VERTX_HTTP_SERVER_INTERNAL'),
    1060: ServiceTypeDef(1060, 'WEBSPHERE'),
    1061: ServiceTypeDef(1061, 'WEBSPHERE_METHOD'),
    1070: ServiceTypeDef(1070, 'WEBLOGIC'),
    1071: ServiceTypeDef(1071, 'WEBLOGIC_METHOD'),
    1080: ServiceTypeDef(1080, 'RESERVED'),
    1081: ServiceTypeDef(1081, 'RESERVED'),
    1100: ServiceTypeDef(1100, 'THRIFT_SERVER'),
    1101: ServiceTypeDef(1101, 'THRIFT_SERVER_INTERNAL'),
    1110: ServiceTypeDef(1110, 'DUBBO_PROVIDER'),
    1120: ServiceTypeDef(1120, 'UNDERTOW'),
    1121: ServiceTypeDef(1121, 'UNDERTOW_METHOD'),
    1126: ServiceTypeDef(1126, 'UNDERTOW_SERVLET_METHOD'),
    1130: ServiceTypeDef(1130, 'GRPC_SERVER'),
    1140: ServiceTypeDef(1140, 'REACTOR_NETTY'),
    1141: ServiceTypeDef(1141, 'REACTOR_NETTY_INTERNAL'),
    1150: ServiceTypeDef(1150, 'ARMERIA'),
    1151: ServiceTypeDef(1151, 'ARMERIA_INTERNAL'),
    1160: ServiceTypeDef(1160, 'KSTOR'),
    1161: ServiceTypeDef(1161, 'KSTOR_INTERNAL'),
    1300: ServiceTypeDef(1300, 'C_CPP'),
    1301: ServiceTypeDef(1301, 'C_CPP_METHOD'),
    1310: ServiceTypeDef(1310, 'AKKA_HTTP_SERVER'),
    1311: ServiceTypeDef(1311, 'AKKA_HTTP_SERVER_INTERNAL'),
    1400: ServiceTypeDef(1400, 'NODE'),
    1401: ServiceTypeDef(1401, 'NODE_METHOD'),
    1500: ServiceTypeDef(1500, 'PHP'),
    1501: ServiceTypeDef(1501, 'PHP_METHOD'),
    1550: ServiceTypeDef(1550, 'ENVOY'),
    1620: ServiceTypeDef(1620, 'OPENWHISK_INTERNAL'),
    1621: ServiceTypeDef(1621, 'OPENWHISK_CONTROLLER'),
    1622: ServiceTypeDef(1622, 'OPENWHISK_INVOKER'),
    1700: ServiceTypeDef(1700, 'PYTHON'),
    1701: ServiceTypeDef(1701, 'PYTHON_METHOD'),
    1702: ServiceTypeDef(1702, 'CELERY'),
    1703: ServiceTypeDef(1703, 'CELERY-WORKER'),
    1800: ServiceTypeDef(1800, 'GO'),
    1801: ServiceTypeDef(1801, 'GO_FUNCTION'),
    2050: ServiceTypeDef(2050, 'UNKNOWN_DB'),
    2051: ServiceTypeDef(2051, 'UNKNOWN_DB_EXECUTE_QUERY'),
    2100: ServiceTypeDef(2100, 'MYSQL'),
    2101: ServiceTypeDef(2101, 'MYSQL_EXECUTE_QUERY'),
    2102: ServiceTypeDef(2102, 'R2DBC_MYSQL'),
    2103: ServiceTypeDef(2103, 'R2DBC_MYSQL_EXECUTE_QUERY'),
    2150: ServiceTypeDef(2150, 'MARIADB'),
    2151: ServiceTypeDef(2151, 'MARIADB_EXECUTE_QUERY'),
    2152: ServiceTypeDef(2152, 'R2DBC_MARIADB'),
    2153: ServiceTypeDef(2153, 'R2DBC_MARIADB_EXECUTE_QUERY'),
    2160: ServiceTypeDef(2160, 'DB2'),
    2161: ServiceTypeDef(2161, 'DB2_EXECUTE_QUERY'),
    2200: ServiceTypeDef(2200, 'MSSQL'),
    2201: ServiceTypeDef(2201, 'MSSQL_EXECUTE_QUERY'),
    2250: ServiceTypeDef(2250, 'MSSQL_JDBC'),
    2251: ServiceTypeDef(2251, 'MSSQL_JDBC_QUERY'),
    2252: ServiceTypeDef(2252, 'R2DBC_MSSQL_JDBC'),
    2253: ServiceTypeDef(2253, 'R2DBC_MSSQL_JDBC_QUERY'),
    2300: ServiceTypeDef(2300, 'ORACLE'),
    2301: ServiceTypeDef(2301, 'ORACLE_EXECUTE_QUERY'),
    2302: ServiceTypeDef(2302, 'R2DBC_ORACLE'),
    2303: ServiceTypeDef(2303, 'R2DBC_ORACLE_EXECUTE_QUERY'),
    2340: ServiceTypeDef(2340, 'DAMENG'),
    2341: ServiceTypeDef(2341, 'DAMENG_EXECUTE_QUERY'),
    2400: ServiceTypeDef(2400, 'CUBRID'),
    2401: ServiceTypeDef(2401, 'CUBRID_EXECUTE_QUERY'),
    2410: ServiceTypeDef(2410, 'NBASET'),
    2411: ServiceTypeDef(2411, 'NBASET_EXECUTE_QUERY'),
    2412: ServiceTypeDef(2412, 'NBASET_INTERNAL'),
    2450: ServiceTypeDef(2450, 'INFORMIX'),
    2451: ServiceTypeDef(2451, 'INFORMIX_EXECUTE_QUERY'),
    2500: ServiceTypeDef(2500, 'POSTGRESQL'),
    2501: ServiceTypeDef(2501, 'POSTGRESQL_EXECUTE_QUERY'),
    2502: ServiceTypeDef(2502, 'R2DBC_POSTGRESQL'),
    2503: ServiceTypeDef(2503, 'R2DBC_POSTGRESQL_EXECUTE_QUERY'),
    2600: ServiceTypeDef(2600, 'CASSANDRA'),
    2601: ServiceTypeDef(2601, 'CASSANDRA_EXECUTE_QUERY'),
    2602: ServiceTypeDef(2602, 'CASSANDRA4'),
    2603: ServiceTypeDef(2603, 'CASSANDRA4_EXECUTE_QUERY'),
    2650: ServiceTypeDef(2650, 'MONGO'),
    2651: ServiceTypeDef(2651, 'MONGO_EXECUTE_QUERY'),
    2652: ServiceTypeDef(2652, 'MONGO_REACTIVE'),
    2700: ServiceTypeDef(2700, 'COUCHDB'),
    2701: ServiceTypeDef(2701, 'COUCHDB_EXECUTE_QUERY'),
    2750: ServiceTypeDef(2750, 'H2'),
    2751: ServiceTypeDef(2751, 'H2_EXECUTE_QUERY'),
    2752: ServiceTypeDef(2752, 'R2DBC_H2'),
    2753: ServiceTypeDef(2753, 'R2DBC_H2_EXECUTE_QUERY'),
    2800: ServiceTypeDef(2800, 'CLICK_HOUSE'),
    2801: ServiceTypeDef(2801, 'CLICK_HOUSE_EXECUTE_QUERY'),
    2810: ServiceTypeDef(2810, 'AWS_SDK_S3'),
    2811: ServiceTypeDef(2811, 'AWS_SDK_S3_INTERNAL'),
    5000: ServiceTypeDef(5000, 'INTERNAL_METHOD'),
    5005: ServiceTypeDef(5005, 'JSP'),
    5010: ServiceTypeDef(5010, 'GSON'),
    5011: ServiceTypeDef(5011, 'JACKSON'),
    5012: ServiceTypeDef(5012, 'JSON-LIB'),
    5013: ServiceTypeDef(5013, 'FASTJSON'),
    5020: ServiceTypeDef(5020, 'JDK_FUTURE'),
    5050: ServiceTypeDef(5050, 'SPRING'),
    5051: ServiceTypeDef(5051, 'SPRING_MVC'),
    5052: ServiceTypeDef(5052, 'SPRING_ASYNC'),
    5053: ServiceTypeDef(5053, 'SPRING_WEBFLUX'),
    5054: ServiceTypeDef(5054, 'SPRING_TX'),
    5055: ServiceTypeDef(5055, 'SPRING_CLOUD_SLEUTH'),
    5071: ServiceTypeDef(5071, 'SPRING_BEAN'),
    5500: ServiceTypeDef(5500, 'IBATIS'),
    5501: ServiceTypeDef(5501, 'IBATIS-SPRING'),
    5510: ServiceTypeDef(5510, 'MYBATIS'),
    6001: ServiceTypeDef(6001, 'THREAD_ASYNC'),
    6005: ServiceTypeDef(6005, 'PROCESS'),
    6050: ServiceTypeDef(6050, 'DBCP'),
    6052: ServiceTypeDef(6052, 'DBCP2'),
    6060: ServiceTypeDef(6060, 'HIKARICP'),
    6062: ServiceTypeDef(6062, 'DRUID'),
    6500: ServiceTypeDef(6500, 'RXJAVA'),
    6510: ServiceTypeDef(6510, 'REACTOR'),
    6520: ServiceTypeDef(6520, 'RESILIENCE4J'),
    6600: ServiceTypeDef(6600, 'EXPRESS'),
    6610: ServiceTypeDef(6610, 'KOA'),
    6620: ServiceTypeDef(6620, 'HAPI'),
    6630: ServiceTypeDef(6630, 'RESTIFY'),
    6640: ServiceTypeDef(6640, 'SPRING_DATA_R2DBC'),
    7010: ServiceTypeDef(7010, 'USER_INCLUDE'),
    8050: ServiceTypeDef(8050, 'MEMCACHED'),
    8051: ServiceTypeDef(8051, 'MEMCACHED_FUTURE_GET'),
    8100: ServiceTypeDef(8100, 'ARCUS'),
    8101: ServiceTypeDef(8101, 'ARCUS_FUTURE_GET'),
    8102: ServiceTypeDef(8102, 'ARCUS_EHCACHE_FUTURE_GET'),
    8103: ServiceTypeDef(8103, 'ARCUS_INTERNAL'),
    8200: ServiceTypeDef(8200, 'REDIS'),
    8201: ServiceTypeDef(8201, 'REDIS_LETTUCE'),
    8202: ServiceTypeDef(8202, 'IOREDIS'),
    8203: ServiceTypeDef(8203, 'REDIS_REDISSON'),
    8204: ServiceTypeDef(8204, 'REDIS_REDISSON_INTERNAL'),
    8205: ServiceTypeDef(8205, 'REDIS_REDISSON_REACTIVE'),
    8206: ServiceTypeDef(8206, 'REDIS_LETTUCE_INTERNAL'),
    8300: ServiceTypeDef(8300, 'RABBITMQ'),
    8670: ServiceTypeDef(8670, 'PULSAR_CLIENT'),
    8671: ServiceTypeDef(8671, 'PULSAR_CLIENT_INTERNAL'),
    8800: ServiceTypeDef(8800, 'HBASE_CLIENT'),
    8803: ServiceTypeDef(8803, 'HBASE_ASYNC_CLIENT'),
    9050: ServiceTypeDef(9050, 'HTTP_CLIENT_3'),
    9051: ServiceTypeDef(9051, 'HTTP_CLIENT_3_INTERNAL'),
    9052: ServiceTypeDef(9052, 'HTTP_CLIENT_4'),
    9053: ServiceTypeDef(9053, 'HTTP_CLIENT_4_INTERNAL'),
    9054: ServiceTypeDef(9054, 'GOOGLE_HTTP_CLIENT_INTERNAL'),
    9055: ServiceTypeDef(9055, 'JDK_HTTPURLCONNECTOR'),
    9056: ServiceTypeDef(9056, 'ASYNC_HTTP_CLIENT'),
    9057: ServiceTypeDef(9057, 'ASYNC_HTTP_CLIENT_INTERNAL'),
    9058: ServiceTypeDef(9058, 'OK_HTTP_CLIENT'),
    9059: ServiceTypeDef(9059, 'OK_HTTP_CLIENT_INTERNAL'),
    9067: ServiceTypeDef(9067, 'JDK_HTTPURLCONNECTOR_INTERNAL'),
    9081: ServiceTypeDef(9081, 'APACHE_CXF_SERVICE_INVOKER'),
    9082: ServiceTypeDef(9082, 'APACHE_CXF_MESSAGE_SENDER'),
    9083: ServiceTypeDef(9083, 'APACHE_CXF_LOGGING_IN'),
    9084: ServiceTypeDef(9084, 'APACHE_CXF_LOGGING_OUT'),
    9100: ServiceTypeDef(9100, 'THRIFT_CLIENT'),
    9101: ServiceTypeDef(9101, 'THRIFT_CLIENT_INTERNAL'),
    9110: ServiceTypeDef(9110, 'DUBBO_CONSUMER'),
    9120: ServiceTypeDef(9120, 'HYSTRIX_COMMAND'),
    9130: ServiceTypeDef(9130, 'VERTX_HTTP_CLIENT'),
    9131: ServiceTypeDef(9131, 'VERTX_HTTP_CLIENT_INTERNAL'),
    9140: ServiceTypeDef(9140, 'REST_TEMPLATE'),
    9150: ServiceTypeDef(9150, 'NETTY'),
    9151: ServiceTypeDef(9151, 'NETTY_INTERNAL'),
    9152: ServiceTypeDef(9152, 'NETTY_HTTP'),
    9153: ServiceTypeDef(9153, 'SPRING_WEBFLUX_CLIENT'),
    9154: ServiceTypeDef(9154, 'REACTOR_NETTY_CLIENT'),
    9155: ServiceTypeDef(9155, 'REACTOR_NETTY_CLIENT_INTERNAL'),
    9156: ServiceTypeDef(9156, 'ARMERIA_CLIENT'),
    9157: ServiceTypeDef(9157, 'ARMERIA_CLIENT_INTERNAL'),
    9160: ServiceTypeDef(9160, 'GRPC'),
    9161: ServiceTypeDef(9161, 'GRPC_INTERNAL'),
    9162: ServiceTypeDef(9162, 'GRPC_SERVER_INTERNAL'),
    9203: ServiceTypeDef(9203, 'ELASTICSEARCH'),
    9204: ServiceTypeDef(9204, 'ELASTICSEARCH_HIGHLEVEL_CLIENT'),
    9205: ServiceTypeDef(9205, 'ELASTICSEARCH8'),
    9206: ServiceTypeDef(9206, 'ELASTICSEARCH8_CLIENT'),
    9301: ServiceTypeDef(9301, 'ENVOY_INGRESS'),
    9302: ServiceTypeDef(9302, 'ENVOY_EGRESS'),
    9401: ServiceTypeDef(9401, 'GO_HTTP_CLIENT'),
    9622: ServiceTypeDef(9622, 'OPENWHISK_CLIENT'),
    9700: ServiceTypeDef(9700, 'PHP_REMOTE_METHOD'),
    9800: ServiceTypeDef(9800, 'C_CPP_REMOTE_METHOD'),
    9900: ServiceTypeDef(9900, 'PYTHON_REMOTE_METHOD'),
    9998: ServiceTypeDef(9998, 'AKKA_HTTP_SERVER_INTERNAL_LEGACY'),
}


def annotation_name(code) -> str:
    """키 코드 -> 이름. 모르면 빈 문자열 (호출 측에서 폴백 처리)."""
    d = ANNOTATION_KEYS.get(code)
    return d.name if d is not None else ""


def service_type_name(code) -> str:
    """서비스 코드 -> 이름. 모르면 빈 문자열 (호출 측에서 코드 표시로 폴백)."""
    d = SERVICE_TYPES.get(code)
    return d.name if d is not None else ""


# ════════════════════════════════════════════════════════
# 1. 로드: JSONL -> 메모리 모델
# ════════════════════════════════════════════════════════

def load_metadata(data_dir: Path) -> tuple[dict, dict]:
    """metadata.jsonl -> (apiId→apiInfo, sqlId→sql)."""
    api: dict[str, str] = {}
    sql: dict[str, str] = {}
    for line in (data_dir / "metadata.jsonl").read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line:
            continue
        o = json.loads(line)
        m, msg = o["method"], o["msg"]
        if m == "RequestApiMetaData":
            api[str(msg.get("apiId"))] = msg.get("apiInfo", "")
        elif m in ("RequestSqlMetaData", "RequestSqlUidMetaData"):
            sql[str(msg.get("sqlId", msg.get("sqlUid", "")))] = msg.get("sql", "")
    return api, sql


def load_spans(data_dir: Path) -> list[dict]:
    """spans.jsonl 한 줄 = PSpanMessage{span|spanChunk}."""
    return [json.loads(line) for line in
            (data_dir / "spans.jsonl").read_text(encoding="utf-8").splitlines()
            if line.strip()]


def tx_key_of(body: dict) -> tuple:
    t = body["transactionId"]
    return (t.get("agentId"), str(t.get("agentStartTime")), str(t.get("sequence")))


def group_by_transaction(span_rows: list[dict]) -> dict[tuple, dict]:
    """같은 transactionId+spanId를 한 요청으로 묶는다 (Node 단위)."""
    groups: dict[tuple, dict] = {}
    for row in span_rows:
        msg = row["msg"]
        if "span" in msg:
            body, kind = msg["span"], "span"
        elif "spanChunk" in msg:
            body, kind = msg["spanChunk"], "spanChunk"
        else:
            continue
        key = tx_key_of(body) + (str(body.get("spanId")),)
        g = groups.setdefault(key, {"root": None, "chunks": []})
        if kind == "span":
            g["root"] = body
        else:
            g["chunks"].append(body)
    return groups


def load_all(data_dir: Path) -> tuple[dict, dict, dict]:
    """(api, sql, groups) 한 번에 로드."""
    api, sql = load_metadata(data_dir)
    return api, sql, group_by_transaction(load_spans(data_dir))


# ════════════════════════════════════════════════════════
# 2. 모델: Align 병합 + CallTree 빌드 + Record 변환
#    (Pinpoint Web 로직의 단순 재현 — 파일 상단 docstring 참고)
# ════════════════════════════════════════════════════════

def norm_event(ev: dict) -> dict:
    """proto 기본값 복원: sequence 누락=0, depth 누락=-1."""
    ev = dict(ev)
    ev["_sequence"] = int(ev.get("sequence", 0))
    ev["_depth"] = int(ev["depth"]) if "depth" in ev else LEVEL_DEPTH
    return ev


def collect_aligns(group: dict) -> tuple[dict | None, list[dict]]:
    """root spanEvent + chunk spanEvent를 sequence 순으로 정렬."""
    root = group["root"]
    events: list[dict] = []
    for ev in (root.get("spanEvent", []) if root else []):
        e = norm_event(ev)
        e["_from"] = "span"
        events.append(e)
    for ch in group["chunks"]:
        is_async = any("asyncEvent" in e for e in ch.get("spanEvent", []))
        for ev in ch.get("spanEvent", []):
            e = norm_event(ev)
            e["_from"] = "spanChunk"
            e["_async"] = is_async or ("asyncEvent" in ev)
            events.append(e)
    events.sort(key=lambda e: (e["_sequence"], e.get("startElapsed", 0)))
    return root, events


@dataclass
class TNode:
    depth: int
    align: dict | None
    is_root: bool = False
    corrupted: str = ""
    children: list["TNode"] = field(default_factory=list)


def build_call_tree(root_span: dict, events: list[dict]) -> tuple[TNode, list[str]]:
    """SpanCallTree.add(depth, align) 재현. 위반 시 corrupted 기록하고 계속."""
    warnings: list[str] = []
    root_node = TNode(depth=ROOT_DEPTH, align={"_kind": "root"}, is_root=True)
    cursor = root_node
    stack: list[TNode] = [root_node]
    expected_seq: int | None = 0 if events else None

    def attach(parent: TNode, node: TNode):
        parent.children.append(node)
        while len(stack) > parent.depth + 1:
            stack.pop()
        stack.append(node)

    for ev in events:
        seq, depth = ev["_sequence"], ev["_depth"]
        eff_depth = cursor.depth if depth == LEVEL_DEPTH else depth

        if expected_seq is not None and seq != expected_seq and not ev.get("_async"):
            warnings.append(f"sequence gap: expected {expected_seq}, got {seq} (depth {depth})")
        expected_seq = seq + 1

        if eff_depth == cursor.depth and not cursor.is_root:  # 형제
            node = TNode(depth=cursor.depth, align=ev)
            attach(stack[cursor.depth - 1] if cursor.depth >= 1 else root_node, node)
            cursor = node
        elif eff_depth == cursor.depth + 1:  # 자식
            node = TNode(depth=eff_depth, align=ev)
            attach(cursor, node)
            cursor = node
        elif eff_depth <= cursor.depth:  # 조상 복귀
            if eff_depth <= ROOT_DEPTH:
                warnings.append(f"invalid depth {depth} (seq {seq}): root 레벨로 삽입")
                node = TNode(depth=1, align=ev, corrupted="invalid-depth")
                attach(root_node, node)
                cursor = node
                continue
            node = TNode(depth=eff_depth, align=ev)
            attach(stack[eff_depth - 1], node)
            cursor = node
        else:  # depth 점프 (+2 이상)
            warnings.append(f"depth jump: cursor {cursor.depth} -> {depth} (seq {seq})")
            node = TNode(depth=cursor.depth + 1, align=ev, corrupted=f"depth-jump:{depth}")
            attach(cursor, node)
            cursor = node

    return root_node, warnings


def annotation_value_to_str(v: dict) -> str:
    if not isinstance(v, dict) or len(v) != 1:
        return json.dumps(v, ensure_ascii=False)
    _, inner = next(iter(v.items()))
    if isinstance(inner, dict):
        return "{" + ", ".join(f"{k}={x}" for k, x in inner.items() if x is not None) + "}"
    return str(inner)


def _cut(s: str, limit: int | None) -> str:
    return s if limit is None else s[:limit]


def resolve_argument(ev: dict, sql: dict, limit: int | None) -> str:
    """SQL-ID→SQL 본문, 그 외 annotation 요약, 없으면 destinationId."""
    for ann in ev.get("annotation", []):
        if ann.get("key") == ANNOTATION_KEY_SQL_ID:
            sid = str(ann.get("value", {}).get("intStringStringValue", {}).get("intValue", ""))
            if sid and sid in sql:
                return _cut(sql[sid], limit)
    anns = ev.get("annotation", [])
    if anns:
        a = anns[0]
        name = annotation_name(a.get("key")) or f"key={a.get('key')}"
        return _cut(f"{name}: {annotation_value_to_str(a.get('value', {}))}", limit)
    return ((ev.get("nextEvent") or {}).get("messageEvent") or {}).get("destinationId", "") or ""


def split_api(api_info: str) -> tuple[str, str, str]:
    """'a.b.C.m(x)' -> (class, method, full)."""
    head = api_info.split("(")[0] if "(" in api_info else api_info
    cls, _, method = head.rpartition(".")
    return cls, method, api_info


def _http_status(root: dict):
    return next((a.get("value", {}).get("intValue")
                 for a in root.get("annotation", [])
                 if a.get("key") == ANNOTATION_KEY_HTTP_STATUS), None)


def build_records(root_span: dict, tree: TNode, api: dict, sql: dict,
                  limit: int | None) -> list[dict]:
    """CallTreeNode -> Record JSON (id/parentId/depth/api/시간/예외/annotation)."""
    records: list[dict] = []
    next_id = [1]
    span_start = int(root_span.get("startTime", 0))  # ms

    def visit(node: TNode, parent_id: int | None):
        nid = next_id[0]
        next_id[0] += 1
        if node.is_root:
            accept = root_span.get("acceptEvent") or {}
            records.append({
                "id": nid, "parentId": None, "depth": 0,
                "kind": "root", "title": accept.get("rpc", "") or "ROOT",
                "api": "Servlet Process",
                "serviceType": root_span.get("serviceType"),
                "serviceTypeName": service_type_name(root_span.get("serviceType")),
                "startTimeMs": span_start, "elapsedMs": root_span.get("elapsed"),
                "httpStatus": _http_status(root_span),
                "endpoint": accept.get("endPoint"),
                "remoteAddr": accept.get("remoteAddr"),
            })
        else:
            ev = node.align or {}
            full = api.get(str(ev.get("apiId", "")), f"apiId={ev.get('apiId', '')}")
            cls, method, _ = split_api(full)
            s = ev.get("startElapsed", 0) or 0
            e = ev.get("endElapsed", 0) or 0
            exc = None
            if ev.get("exceptionInfo"):
                ei = ev["exceptionInfo"]
                exc = {"intValue": ei.get("intValue"),
                       "message": _cut(ei.get("stringValue") or "", limit)}
            records.append({
                "id": nid, "parentId": parent_id, "depth": node.depth,
                "kind": "spanChunk" if ev.get("_from") == "spanChunk" else "spanEvent",
                "sequence": ev.get("_sequence"),
                "title": method or _cut(full, 120),
                "api": full if limit is None else _cut(full, 250),
                "className": cls,
                "serviceType": ev.get("serviceType"),
                "serviceTypeName": service_type_name(ev.get("serviceType")),
                "argument": resolve_argument(ev, sql, limit),
                "destinationId": ((ev.get("nextEvent") or {}).get("messageEvent") or {}).get("destinationId", ""),
                "startTimeMs": span_start + s, "elapsedMs": max(0, e - s),
                "startOffsetMs": s, "endOffsetMs": e,
                "exception": exc,
                "annotations": [{"key": a.get("key"),
                                 "name": annotation_name(a.get("key")),
                                 "value": _cut(annotation_value_to_str(a.get("value", {})), limit)}
                                for a in ev.get("annotation", [])],
                "async": bool(ev.get("_async") or ev.get("asyncEvent")),
                "corrupted": node.corrupted or "",
            })
        for ch in node.children:
            visit(ch, nid)

    visit(tree, None)
    return records


def analyze_transaction(group_key: tuple, group: dict, api: dict, sql: dict,
                        limit: int | None = DEFAULT_TRUNCATE) -> dict:
    root, events = collect_aligns(group)
    if root is None:
        return {"error": "root span 없음 (chunk만 존재)", "key": str(group_key)}
    tree, warnings = build_call_tree(root, events)
    agent_id, start_time, seq, span_id = group_key
    return {
        "transactionId": {"agentId": agent_id, "agentStartTime": start_time,
                          "sequence": seq, "spanId": span_id},
        "rpc": (root.get("acceptEvent") or {}).get("rpc"),
        "httpStatus": _http_status(root),
        "elapsedMs": root.get("elapsed"),
        "startTimeMs": root.get("startTime"),
        "eventCount": len(events),
        "chunkCount": len(group["chunks"]),
        "warnings": warnings,
        "records": build_records(root, tree, api, sql, limit),
    }


def list_transactions(groups: dict, rpc: str | None = None, status: int | None = None,
                      sort: str = "tx", limit: int | None = None) -> list[dict]:
    out = []
    for key, g in groups.items():
        root = g["root"]
        if root is None:
            out.append({"tx": key[2], "rpc": "<no-root>", "elapsedMs": None,
                        "events": 0, "chunks": len(g["chunks"])})
            continue
        r = (root.get("acceptEvent") or {}).get("rpc", "")
        st = _http_status(root)
        if rpc and rpc not in r:
            continue
        if status is not None and st != status:
            continue
        n = len(root.get("spanEvent", [])) + sum(len(c.get("spanEvent", [])) for c in g["chunks"])
        out.append({"tx": key[2], "rpc": r, "httpStatus": st,
                    "elapsedMs": root.get("elapsed"), "events": n, "chunks": len(g["chunks"])})
    if sort == "elapsed":
        out.sort(key=lambda r: (r["elapsedMs"] is None, -(r["elapsedMs"] or 0)))
    else:
        out.sort(key=lambda r: int(r["tx"]))
    return out[:limit] if limit else out


# ════════════════════════════════════════════════════════
# 3. 보기: 사람용 렌더 (JSON은 그대로 출력)
# ════════════════════════════════════════════════════════

def render_tree(payload: dict, limit: int | None = DEFAULT_TRUNCATE,
                full_name: bool = False, show_arg: bool = True) -> str:
    """Pinpoint CallTree 화면처럼 ASCII 트리로.

    full_name=True면 풀패키지(class.method), 기본은 메서드명만.
    show_arg=False면 :: SQL(argument) 숨기기, 기본은 보이기.
    """
    arg_len = None if limit is None else TREE_ARG_LEN
    lines = [f"{payload.get('rpc')} [{payload.get('httpStatus')}] {payload.get('elapsedMs')}ms "
             f"(tx={payload.get('transactionId', {}).get('sequence')}, "
             f"events={payload.get('eventCount')})"]
    children: dict[int, list[dict]] = defaultdict(list)
    roots = []
    for r in payload.get("records", []):
        (roots if r.get("parentId") is None else children[r["parentId"]]).append(r)

    total = payload.get("elapsedMs") or 0

    def visit(r: dict, prefix: str, is_last: bool):
        if r.get("kind") == "root":
            kids = children.get(r["id"], [])
            for i, ch in enumerate(kids):
                visit(ch, "", i == len(kids) - 1)
            return
        cls, method = r.get("className", ""), r.get("title", "")
        if full_name:
            name = f"{cls}.{method}" if cls else (r.get("api", "") or method)
        else:
            name = method or r.get("api", "")
        el = r.get("elapsedMs")
        pct = f"{el / total * 100:.1f}%" if total and el is not None else "-"
        arg = (r.get("argument", "") or "") if show_arg else ""
        extra = "".join([
            f" :: {arg if arg_len is None else arg[:arg_len]}" if arg else "",
            f" !! {r['exception']['message'][:100]}" if r.get("exception") else "",
            f" [corrupted:{r['corrupted']}]" if r.get("corrupted") else "",
            " [async]" if r.get("async") else "",
        ])
        lines.append(f"{prefix}{'└─ ' if is_last else '├─ '}[{r['depth']}:{r.get('sequence')}] "
                     f"{name} ({r.get('serviceTypeName') or r.get('serviceType')}) "
                     f"{el}ms ({pct}){extra}")
        kids = children.get(r["id"], [])
        for i, ch in enumerate(kids):
            visit(ch, prefix + ("   " if is_last else "│  "), i == len(kids) - 1)

    for r in roots:
        visit(r, "", True)
    lines.extend(f"! {w}" for w in payload.get("warnings", []))
    return "\n".join(lines)


def to_yaml(obj, indent: int = 0) -> str:
    """의존성 없는 최소 YAML 방출기. JSON 호환 값(dict/list/str/num/bool/None) 전용.
    문자열은 "" 인용(JSON 인용은 유효한 YAML), 개행 포함 시 |- 블록."""
    pad = "  " * indent
    if obj is None:
        return "null"
    if obj is True:
        return "true"
    if obj is False:
        return "false"
    if isinstance(obj, (int, float)):
        return repr(obj)
    if isinstance(obj, str):
        if "\n" in obj:
            body = "\n".join(f"{pad}  {ln}" if ln.strip() else ""
                               for ln in obj.rstrip("\n").split("\n"))
            return f"|-\n{body}"
        return json.dumps(obj, ensure_ascii=False)
    if isinstance(obj, list):
        if not obj:
            return "[]"
        out = []
        for v in obj:
            if isinstance(v, dict) and v:
                items = list(v.items())
                k0, v0 = items[0]
                s0 = to_yaml(v0, indent + 1)
                out.append(f"{pad}- {k0}:\n{s0}" if "\n" in s0 else f"{pad}- {k0}: {s0}")
                for k, vv in items[1:]:
                    s = to_yaml(vv, indent + 1)
                    out.append(f"{pad}  {k}:\n{s}" if "\n" in s else f"{pad}  {k}: {s}")
            else:
                s = to_yaml(v, indent + 1)
                if "\n" in s:
                    first, rest = s.split("\n", 1)
                    rest = "\n".join(f"{pad}  {ln}" if ln.strip() else ""
                                       for ln in rest.split("\n"))
                    out.append(f"{pad}- {first.strip()}\n{rest}")
                else:
                    out.append(f"{pad}- {s.strip()}")
        return "\n".join(out)
    if isinstance(obj, dict):
        if not obj:
            return "{}"
        out = []
        for k, v in obj.items():
            s = to_yaml(v, indent + 1)
            out.append(f"{pad}{k}:\n{s}" if "\n" in s else f"{pad}{k}: {s}")
        return "\n".join(out)
    return json.dumps(str(obj), ensure_ascii=False)


# ════════════════════════════════════════════════════════
# 4. CLI: <action> [--option]
#    list             목록 (필터: --rpc/--status, 정렬/제한)
#    show             1건 상세 (렌더: tree/json/yaml)
# ════════════════════════════════════════════════════════

def _emit_json(payload, out: str | None, compact: bool):
    text = json.dumps(payload, ensure_ascii=False) if compact \
        else json.dumps(payload, ensure_ascii=False, indent=2)
    if out:
        Path(out).write_text(text, encoding="utf-8")
        print(f"wrote {out} ({len(text)} chars)")
    else:
        print(text)


def _select(groups: dict, tx: str | None, rpc: str | None,
            status: int | None, pick: str | None) -> tuple[list, str | None]:
    """선택 조건 4개는 수평 관계(위계 없음). 전부 AND로 겹치고, 없으면 전체."""
    candidates = list(groups.items())
    if tx is not None:
        candidates = [(k, g) for k, g in candidates if k[2] == str(tx)]
    if rpc:
        candidates = [(k, g) for k, g in candidates
                      if rpc in (((g["root"] or {}).get("acceptEvent") or {}).get("rpc", ""))]
    if status is not None:
        candidates = [(k, g) for k, g in candidates
                      if g["root"] is not None and _http_status(g["root"]) == status]
    if not candidates:
        return [], "조건에 맞는 트랜잭션 없음 (--tx/--rpc/--status 확인)"
    if pick == "latest":
        candidates = [sorted(candidates, key=lambda kv: int(kv[0][2]))[-1]]
    elif pick == "first":
        candidates = [sorted(candidates, key=lambda kv: int(kv[0][2]))[0]]
    elif pick == "slowest":
        candidates = [sorted(candidates,
                             key=lambda kv: -((kv[1]["root"] or {}).get("elapsed") or 0))[0]]
    return candidates, None


def _analyze_many(groups: dict, api: dict, sql: dict, args, limit: int | None) -> list[dict]:
    candidates, err = _select(groups, args.tx, args.rpc, args.status, args.pick)
    if err:
        print(err, file=sys.stderr)
        raise SystemExit(1)
    return [analyze_transaction(k, g, api, sql, limit) for k, g in candidates]


def cmd_list(args) -> int:
    _, _, groups = load_all(Path(args.data_dir))
    _emit_json(list_transactions(groups, rpc=args.rpc, status=args.status,
                                 sort=args.sort, limit=args.limit),
               args.out, compact=False)
    return 0


def _emit_yaml(payload, out: str | None):
    text = to_yaml(payload)
    if out:
        Path(out).write_text(text, encoding="utf-8")
        print(f"wrote {out} ({len(text)} chars)")
    else:
        print(text)


def _reject(msg: str) -> int:  # 축 어긋남: argparse error() 상당, exit 2
    print(f"analyze.py: error: {msg} (`help` 참조)", file=sys.stderr)
    return 2


def cmd_show(args) -> int:
    """show: 출력방식 1축(--format)으로 결정. json/yaml은 항상 full(자름 없음)."""
    if args.format in ("json", "yaml") and (args.name != "short" or args.sql != "show"):
        return _reject("--name/--sql은 --format tree일 때만")
    if args.compact and not (args.format == "json" or args.out):
        return _reject("--compact는 JSON이 나갈 때만 (--format json 또는 --out 병행)")
    api, sql, groups = load_all(Path(args.data_dir))
    limit = None if (args.sql == "full" or args.format in ("json", "yaml")) else DEFAULT_TRUNCATE
    results = _analyze_many(groups, api, sql, args, limit)
    single = results[0] if len(results) == 1 else results
    if args.format == "json":
        _emit_json(single, args.out, args.compact)
        return 0
    if args.format == "yaml":
        _emit_yaml(single, args.out)
        return 0
    if args.out:  # --out은 항상 JSON 저장, 화면에는 트리 출력
        _emit_json(single, args.out, args.compact)
    for p in results:
        if len(results) > 1:
            print(f"── tx={p.get('transactionId', {}).get('sequence')} "
                  f"{p.get('rpc')} [{p.get('httpStatus')}] ──")
        print(render_tree(p, limit, full_name=(args.name == "full"),
                          show_arg=(args.sql != "hide")))
    return 0


# ════════════════════════════════════════════════════════
# 5. 도움말: help [list|show] — --help보다 자세한 설명
# ════════════════════════════════════════════════════════

HELP_OVERVIEW = """
개요
----
mini-collector가 저장한 JSONL을 읽어,
Pinpoint Web의 CallStack(CallTree) 1개 트랜잭션을 재현한다.

읽는 파일 (--data-dir, 기본값은 실행 파일 기준 ../collector/mini-collector-data):
- spans.jsonl   : PSpanMessage{span|spanChunk} — 호출 트리 원본
- metadata.jsonl: apiId→apiInfo, sqlId→sql — 이름 해결용
- stat.jsonl    : JVM/URI 통계. 이 도구는 읽지 않는다.

전체 구조:
([ ]=축 — 분류일 뿐 실제 옵션이 아님 / --opt=실제 옵션 /
 A|B=택1 / A+B=AND 결합 / 대문자=값이 들어갈 자리)
  .
  ├── list                                  # 목록 (스캔)
  │   ├── [원본위치] --data-dir DIR
  │   ├── [출력위치] --out PATH             # 항상 JSON 저장
  │   ├── [조건식] --rpc SUBSTR + --status CODE
  │   └── [목록] --sort KEY + --limit N
  └── show                                  # 상세 (1 트랜잭션)
      ├── [원본위치] --data-dir DIR
      ├── [출력위치] --out PATH             # 항상 JSON 저장
      ├── [조건식] --tx SEQ + --rpc SUBSTR + --status CODE + --pick PICK
      └── [출력방식] --format MODE          # 택1
          ├── tree                          # 기본, 사람용
          │   ├── --name short|full         # 기본 short
          │   └── --sql show|hide|full      # 기본 show
          ├── json                          # 기계용, 항상 전체
          └── yaml                          # 기계용, 항상 전체
""".strip()

HELP_CONDITIONS = """
조건식: 무엇을 고르나 (list/show 공통, 없으면 전체, 여러 개면 AND)
---------------------------------------------------------------
- --tx SEQ                    : 트랜잭션 번호 직접 지정
- --rpc SUBSTR                : rpc 부분일치 (acceptEvent.rpc = HTTP URI 템플릿)
- --status CODE               : 응답 상태코드 (annotation key=46 http.status.code)
- --pick latest|first|slowest: 다건 매칭 시 1건 선택. show 전용 (list는 --limit로).
                              단건 매칭이면 아무 일도 안 한다.
""".strip()

HELP_LIST = """
list: 모든 HTTP 요청 목록
------------------------
한 줄=1 요청: tx(번호), rpc, httpStatus, elapsedMs, events, chunks.
조건식(--rpc/--status)으로 좁히고, --sort tx|elapsed 정렬,
--limit N으로 자른다. 병목 헌팅은 --sort elapsed.

예:
  list --sort elapsed --limit 5
  list --status 500
""".strip()

HELP_SHOW = """
show: 1개 요청 상세 (기본 --format tree)
--------------------------------------
출력방식 (--format, 택1):
- tree (기본): 사람용 ASCII CallTree
- json / yaml: 기계용. 항상 전체(자름 없음)

출력조건 (tree 전용, 각각 1개 질문 = 1개 옵션):
- --name short|full    : 함수명을 어떻게? short=메서드명만(기본), full=class.method
- --sql show|hide|full: SQL을 어떻게? show=보이기+120자 자름(기본),
                        hide=숨기기, full=전문

직렬화·위치:
- --compact   : JSON 한 줄 출력. JSON이 나갈 때만
                (--format json 또는 --out 병행). YAML에는 없음
- --out PATH  : 화면 출력과 병행해 JSON 스냅샷 저장 (--format과 무관)
- --data-dir D: JSONL 위치 변경

매칭이 여러 건이면 순서대로 전부 출력하고,
각 결과 앞에 `── tx=N ... ──` 구분줄이 붙는다.

예:
  show --tx 20 --format tree --name full --sql full
  show --tx 42
  show --rpc orders --status 200 --pick latest
  show --tx 42 --format json --out /tmp/tx42.json
  show --tx 42 --format yaml | head -n 20
""".strip()

HELP_RULES = """
조합 규칙
---------
- 4축(원본위치/출력위치/조건식/출력방식)은 직교: 어떤 조합이든 가능.
- scope 어긋남은 에러 종료(exit 2), 조용히 무시하지 않는다:
  --name/--sql + --format json|yaml, JSON 안 나가는 --compact.
""".strip()

HELP_TREE = """
tree 한 줄 읽기
---------------
  └─ [5:8] prepareStatement (POSTGRESQL) 0ms (0.0%) :: insert into ...
       └─┬             │         │        │    │          └ argument (:: SQL 본문·어노테이션·목적지)
         │             │         │        │    └ 전체 시간 대비 비율
         │             │         │        └ 이 노드 소요 시간
         │             │         └ Pinpoint ServiceType명
         │             └ 함수명 (기본 메서드명만, --name full로 풀패키지)
         └ [depth:sequence] — 깊이와 호출 순서

줄 끝 표시: !! 예외메시지(실패 노드) / [async](비동기) / [corrupted](수집 결함).
트리 끝에 `! ...` 줄이 있으면 sequence/depth 어긋남 경고다.
""".strip()

HELP_JSON = """
JSON 스키마 (YAML도 동일 구조)
-----------------------------
최상위: transactionId{agentId, agentStartTime, sequence, spanId},
  rpc, httpStatus, elapsedMs, startTimeMs, eventCount, chunkCount,
  warnings[], records[].

record 1건: id/parentId/depth(트리 복원용), kind(root|spanEvent|spanChunk),
  sequence, title(메서드명), api(전체 시그니처), className,
  serviceType(+serviceTypeName), argument(SQL 본문 등), destinationId,
  startTimeMs/elapsedMs/startOffsetMs/endOffsetMs,
  exception{intValue, message}|null, annotations[{key, name, value}],
  async, corrupted.

예:
  show --tx 42 --format json --compact | jq '.records | length'
  show --tx 42 --format json --compact | jq '[.records[] | select(.exception)]'
""".strip()

HELP_SECTIONS = {
    "overview": HELP_OVERVIEW,
    "conditions": HELP_CONDITIONS,
    "list": HELP_LIST,
    "show": HELP_SHOW,
    "rules": HELP_RULES,
    "tree": HELP_TREE,
    "json": HELP_JSON,
}
HELP_ORDER = ["overview", "conditions", "list", "show", "rules", "tree", "json"]


def cmd_help(args) -> int:
    print("\n\n".join(HELP_SECTIONS[t] for t in HELP_ORDER))
    return 0


def build_parser() -> argparse.ArgumentParser:
    ap = argparse.ArgumentParser(
        prog="analyze.py", description="mini-collector JSONL -> 단일 요청 CallStack JSON")
    # --rpc = 요청 이름 부분일치. Pinpoint의 rpc = HTTP URI 템플릿 (원시 acceptEvent.rpc)
    # --status = 응답 HTTP 상태코드 (원시 annotation key=46 http.status.code)
    sub = ap.add_subparsers(dest="action", required=True, metavar="<action>")

    p_list = sub.add_parser("list", help="모든 HTTP 요청 목록")
    g = p_list.add_argument_group("조건식: 무엇을 고르나")
    g.add_argument("--rpc", metavar="SUBSTR", help="요청 이름 부분일치 (예: --rpc orders)")
    g.add_argument("--status", type=int, default=None, metavar="CODE",
                   help="HTTP 상태코드 (예: --status 500)")
    g = p_list.add_argument_group("목록: 어떻게 자르나")
    g.add_argument("--sort", choices=["tx", "elapsed"], default="tx")
    g.add_argument("--limit", type=int, default=None, help="최대 건수")
    g = p_list.add_argument_group("원본위치: 어디서 읽나")
    g.add_argument("--data-dir", default=str(DEFAULT_DATA_DIR))
    g = p_list.add_argument_group("출력위치: 어디에 쓰나 (항상 JSON 저장)")
    g.add_argument("--out", help="결과 JSON 저장 경로")
    p_list.set_defaults(func=cmd_list)

    p_show = sub.add_parser("show", help="1개 요청 상세 (기본 --format tree)")
    g = p_show.add_argument_group("조건식: 무엇을 고르나 (없으면 전체, 수평 AND)")
    g.add_argument("--tx", default=None, metavar="SEQ", help="트랜잭션 번호 (예: --tx 42)")
    g.add_argument("--rpc", metavar="SUBSTR", help="요청 이름 부분일치 (예: --rpc orders)")
    g.add_argument("--status", type=int, default=None, metavar="CODE",
                   help="HTTP 상태코드 (예: --status 500)")
    g.add_argument("--pick", choices=["latest", "first", "slowest"], default=None,
                   help="여러 건 매칭 시 1건만: latest(마지막) first(처음) slowest(가장 느림)")
    g = p_show.add_argument_group("출력방식: 어떻게 보여주나 (택1)")
    g.add_argument("--format", choices=["tree", "json", "yaml"], default="tree",
                   help="tree=사람용(기본), json/yaml=기계용(항상 전체, 자름 없음)")
    g = p_show.add_argument_group("출력조건: --format tree일 때만")
    g.add_argument("--name", choices=["short", "full"], default="short",
                   help="함수명 표시: short=메서드명만(기본), full=class.method 풀패키지")
    g.add_argument("--sql", choices=["show", "hide", "full"], default="show",
                   help="SQL 표시: show=보이기+120자 자름(기본), hide=숨기기, full=전문")
    g = p_show.add_argument_group("직렬화: JSON 한 줄로 (JSON이 나갈 때만)")
    g.add_argument("--compact", action="store_true",
                   help="--format json 또는 --out 병행 시에만 유효")
    g = p_show.add_argument_group("원본위치: 어디서 읽나")
    g.add_argument("--data-dir", default=str(DEFAULT_DATA_DIR))
    g = p_show.add_argument_group("출력위치: 어디에 쓰나 (항상 JSON 저장)")
    g.add_argument("--out", help="화면 출력과 병행해 결과 JSON 저장")
    p_show.set_defaults(func=cmd_show)

    p_help = sub.add_parser("help", help="자세한 사용 설명")
    p_help.set_defaults(func=cmd_help)
    return ap


def main(argv=None) -> int:
    args = build_parser().parse_args(argv)
    return args.func(args)


if __name__ == "__main__":
    raise SystemExit(main())
