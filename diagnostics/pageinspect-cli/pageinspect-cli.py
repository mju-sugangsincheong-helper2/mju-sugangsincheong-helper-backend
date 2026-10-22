#!/usr/bin/env python3
# /// script
# requires-python = ">=3.13"
# dependencies = ["psycopg[binary]>=3.2", "pyyaml>=6", "tabulate>=0.10.0"]
# ///
"""pageinspect-cli: PostgreSQL pageinspect 확장을 위한 단일 파일 CLI.

실행:
    uv run pageinspect-cli.py --help
    uv run pageinspect-cli.py --version
    uv run pageinspect-cli.py --db 'postgresql://user:pass@localhost:5432/mydb' \\
        page header public.my_table --block 0

테스트:
    uv run pytest

설계 기준:
- 단일 파일(pageinspect-cli.py)로 진행
- 원본 pageinspect 함수의 인수·반환 필드를 보존하고 임의로 통합·변경하지 않음
- 출력 형식(table/json/yaml)은 검사 기능과 독립적으로 지정
- 명령 정의는 COMMANDS 단일 스펙에서 생성 (파서·디스패치·함수 검사의 single source of truth)
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import time
from dataclasses import dataclass, field
from typing import Any, Callable, Sequence
from urllib.parse import quote, unquote, urlencode, urlsplit, urlunsplit

__version__ = "0.2.0"

# ---------------------------------------------------------------------------
# 종료 코드 (오류 구분용)
# ---------------------------------------------------------------------------
EXIT_OK = 0
EXIT_USAGE = 2        # 잘못된 명령 또는 인수
EXIT_CONNECTION = 3   # 연결 실패 / 인증 실패
EXIT_QUERY = 4        # 확장·함수 부재, 잘못된 릴레이션, 블록 범위, 버전 문제 등
EXIT_OUTPUT = 5       # 출력 파일 쓰기 실패

VALID_FORMATS = ("table", "json", "yaml")
VALID_FORKS = ("main", "fsm", "vm", "init")
FORK_ALIASES = {"visibilitymap": "vm"}
MAX_RANGE_COUNT = 10000
MIN_SERVER_VERSION_NUM = 130000  # PG13: bytea형 바리언트 등 구버전 폴백 기준점


class CliError(Exception):
    """종료 코드가 정해진 CLI 오류."""

    def __init__(self, message: str, code: int = EXIT_QUERY):
        super().__init__(message)
        self.code = code


# ---------------------------------------------------------------------------
# 연결 URI 유틸
# ---------------------------------------------------------------------------
def mask_password(uri: str) -> str:
    """연결 URI의 비밀번호를 마스킹한다. 로그·오류 출력용.

    다중 호스트(콤마 구분) URI·키워드 형식 모두에서 비밀번호를 마스킹한다.
    사용자 이름은 식별용으로 남기고 비밀번호만 가린다.
    """
    if not uri:
        return ""
    if "://" in uri:
        masked = re.sub(r"(://[^/:@\s]+:)[^/@\s]+@", r"\1***@", uri)
        masked = re.sub(r"([?&](?:sslpassword|password)=)[^&\s]*", r"\1***", masked)
        return masked
    return re.sub(
        r"((?:^|\s)(?:password|sslpassword)\s*=\s*)('(?:[^']|'')*'|\S+)",
        r"\1***",
        uri,
    )


# URI에 없을 때만 부여되는 CLI 기본값 (표준 파라미터, 사용자 지정이 있으면 그것을 우선한다)
CONNINFO_DEFAULTS = {
    "connect_timeout": "10",
    "application_name": "pageinspect-cli",
}


SCHEMA_GUIDANCE = (
    "연결 URI에 비표준 `schema` 파라미터가 있습니다. `schema`는 일부 ORM(Prisma·TypeORM 등)의 "
    "자체 확장으로, libpq 표준이 아닙니다. 스키마 지정은 libpq 표준 `options` 파라미터를 사용하세요: "
    "`?options=-c%20search_path%3D스키마` (공백=%20, 등호=%3D로 URL 인코딩)."
)


def prepare_conninfo(uri: str) -> str:
    """libpq 표준을 그대로 따른다. URI를 파싱·변조하지 않고 전달하는 것이 원칙이다.

    - `postgresql://user:pass@host1:5432,host2:5433/db?k=v&...` 다중 호스트 원문 전달
      (libpq가 직접 처리: target_session_attrs 등과 함께 HA/복제 구성 지원)
    - libpq 표준 쿼리 파라미터 그대로 전달: sslmode/sslcert/sslkey/sslrootcert,
      connect_timeout, target_session_attrs, application_name, options 등
    - 스키마 지정은 표준 `options=-c search_path=...`로 한다 (아래 예시 참조).
      비표준 `schema` 파라미터는 libpq가 거부하므로, 연결 전에 감지해 중단하고
      표준 대안을 안내한다.
    - `connect_timeout`/`application_name`이 없으면 기본값(10초 / pageinspect-cli) 부여.
    - `host=.. dbname=..` 키워드 형식도 그대로 받아들인다.

    반환: libpq용 conninfo 문자열
    """
    if "://" not in uri:
        if re.search(r"(?:^|\s)schema\s*=", uri):
            raise ValueError(SCHEMA_GUIDANCE)
        return _with_conninfo_defaults(uri)
    try:
        parts = urlsplit(uri)
    except Exception as e:
        raise ValueError(f"잘못된 연결 URI: {e}") from e
    # 주의: parse_qsl/urlencode 기본값은 `+`를 공백으로 취급하지만,
    # libpq는 `+`를 문자 그대로 취급한다. unquote + quote_via=quote(%20)로
    # 파싱·재조립해야 `-c%20search_path%3D...` 같은 options가 깨지지 않는다.
    pairs = _split_query(parts.query)
    if any(k == "schema" for k, _ in pairs):
        raise ValueError(SCHEMA_GUIDANCE)
    rest = list(pairs)
    keys = {k for k, _ in rest}
    for k, default in CONNINFO_DEFAULTS.items():
        if k not in keys:
            rest.append((k, default))
    new_query = urlencode(rest, quote_via=quote)
    return urlunsplit((parts.scheme, parts.netloc, parts.path, new_query, parts.fragment))


def _split_query(query: str) -> list[tuple[str, str]]:
    """libpq 의미 그대로 쿼리스트링 분리 (`+`를 공백으로 바꾸지 않음)."""
    pairs: list[tuple[str, str]] = []
    for part in query.split("&"):
        if not part:
            continue
        k, _, v = part.partition("=")
        pairs.append((unquote(k), unquote(v)))
    return pairs


def _with_conninfo_defaults(conninfo: str) -> str:
    """키워드 형식 conninfo에 빠져 있는 표준 기본값만 보충한다."""
    cleaned = re.sub(r"\s+", " ", conninfo).strip()
    for k, default in CONNINFO_DEFAULTS.items():
        if not re.search(rf"(?:^|\s){re.escape(k)}\s*=", cleaned):
            cleaned = f"{cleaned} {k}={default}".strip()
    return cleaned


# ---------------------------------------------------------------------------
# 인수 값 검증
# ---------------------------------------------------------------------------
def non_negative_int(name: str) -> Callable[[str], int]:
    def conv(v: str) -> int:
        try:
            n = int(v)
        except ValueError:
            raise argparse.ArgumentTypeError(f"{name}에는 정수 필요 (입력: {v!r})")
        if n < 0:
            raise argparse.ArgumentTypeError(f"{name}에는 0 이상 정수 필요 (입력: {v})")
        return n

    return conv


def positive_int(name: str) -> Callable[[str], int]:
    def conv(v: str) -> int:
        try:
            n = int(v)
        except ValueError:
            raise argparse.ArgumentTypeError(f"{name}에는 정수 필요 (입력: {v!r})")
        if n < 1:
            raise argparse.ArgumentTypeError(f"{name}에는 1 이상 정수 필요 (입력: {v})")
        return n

    return conv


def validate_relation(rel: str) -> str:
    if not rel or not rel.strip():
        raise ValueError("RELATION이 비어 있습니다. 예: public.my_table")
    if "\x00" in rel:
        raise ValueError("RELATION에 NUL 문자를 사용할 수 없습니다.")
    if len(rel) > 256:
        raise ValueError("RELATION이 너무 깁니다 (256자 이하).")
    return rel


def normalize_fork(fork: str | None) -> str | None:
    if fork is None:
        return None
    f = FORK_ALIASES.get(fork.lower(), fork.lower())
    if f not in VALID_FORKS:
        raise ValueError(f"지원하지 않는 포크: {fork!r} (허용: {', '.join(VALID_FORKS)})")
    return f


def raw_page_call(relation: str, fork: str | None, block: int) -> tuple[str, list[Any]]:
    """get_raw_page(...) 호출 식과 바인드 파라미터를 만든다.

    식별자·값은 문자열 보간 없이 바인드 파라미터로 전달한다.
    """
    if fork is None or fork == "main":
        return "get_raw_page(%s, %s)", [relation, block]
    return "get_raw_page(%s, %s, %s)", [relation, fork, block]


# ---------------------------------------------------------------------------
# DB 계층
# ---------------------------------------------------------------------------
def import_psycopg():
    try:
        import psycopg  # type: ignore
        from psycopg.rows import dict_row  # type: ignore

        return psycopg, dict_row
    except ImportError as e:
        raise CliError(
            "psycopg가 설치되어 있지 않습니다. "
            "diagnostics/pageinspect-cli 디렉터리에서 `uv sync`를 실행하세요.",
            EXIT_CONNECTION,
        ) from e


def import_tabulate():
    try:
        from tabulate import tabulate  # type: ignore

        return tabulate
    except ImportError as e:
        raise CliError(
            "표 출력에 tabulate가 필요합니다. "
            "diagnostics/pageinspect-cli에서 `uv sync`를 실행하세요.",
            EXIT_QUERY,
        ) from e


def connect_db(conninfo: str, original_uri: str):
    """DB 연결. conninfo는 prepare_conninfo()를 거친 libpq용 문자열.

    오류 출력에는 원본이 아닌 마스킹된 URI만 사용한다.
    """
    psycopg, dict_row = import_psycopg()
    try:
        conn = psycopg.connect(conninfo, row_factory=dict_row)
        conn.autocommit = True
        return conn
    except Exception as e:  # OperationalError 하위 포함, 비밀번호 노출 금지
        sqlstate = getattr(e, "sqlstate", "") or ""
        msg = str(e)
        masked = mask_password(original_uri)
        low = msg.lower()
        if sqlstate == "28P01" or "password authentication failed" in low:
            raise CliError(
                f"인증 실패: 사용자 이름 또는 비밀번호를 확인하세요 (DB: {masked}).",
                EXIT_CONNECTION,
            ) from e
        hint = ""
        if "invalid" in low and ("option" in low or "parameter" in low or "uri" in low):
            hint = (
                " 연결 URI의 쿼리 파라미터를 확인하세요"
                " (libpq 표준: connect_timeout/application_name/target_session_attrs/sslmode/sslrootcert/options,"
                " 스키마 지정은 `?options=-c%20search_path%3D스키마` 형식을 사용하세요)."
            )
        raise CliError(
            f"데이터베이스 연결 실패 (DB: {masked}): {short_sql_error(msg)}.{hint}",
            EXIT_CONNECTION,
        ) from e


def short_sql_error(msg: str, limit: int = 300) -> str:
    s = " ".join(msg.split())
    return s if len(s) <= limit else s[:limit] + "..."


def server_version(conn) -> tuple[int, str]:
    with conn.cursor() as cur:
        cur.execute("SHOW server_version_num;")
        num = int(cur.fetchone()["server_version_num"])
        cur.execute("SHOW server_version;")
        ver = str(cur.fetchone()["server_version"])
    return num, ver


def check_extension(conn, masked_uri: str) -> None:
    """pageinspect 확장 설치 여부를 확인한다. 없으면 경고가 아닌 오류로 중단한다."""
    with conn.cursor() as cur:
        cur.execute("SELECT current_database() AS db;")
        dbname = cur.fetchone()["db"]
        cur.execute("SELECT 1 AS ok FROM pg_extension WHERE extname = 'pageinspect';")
        if cur.fetchone() is None:
            raise CliError(
                f"데이터베이스 '{dbname}'에 'pageinspect' 확장이 설치되어 있지 않습니다. "
                f"해당 DB에 권한 있는 사용자로 `CREATE EXTENSION pageinspect;`를 실행한 뒤 다시 시도하세요 "
                f"(연결: {masked_uri}).",
                EXIT_QUERY,
            )


def functions_present(conn, names: list[str]) -> set[str]:
    with conn.cursor() as cur:
        cur.execute(
            "SELECT DISTINCT proname FROM pg_proc WHERE proname = ANY(%s);",
            [names],
        )
        return {r["proname"] for r in cur.fetchall()}


def ensure_functions(conn, required_any: list[list[str]]) -> None:
    """required_any: 각 원소는 OR 관계의 함수명 목록."""
    if not required_any:
        return
    wanted = sorted({n for group in required_any for n in group})
    present = functions_present(conn, wanted)
    missing_groups = [g for g in required_any if not any(n in present for n in g)]
    if missing_groups:
        missing = ", ".join("/".join(g) for g in missing_groups)
        raise CliError(
            f"pageinspect 확장 또는 필요한 함수 부재: {missing}. "
            "`CREATE EXTENSION pageinspect;` 여부와 PostgreSQL 버전을 확인하세요.",
            EXIT_QUERY,
        )


def fetch(conn, sql: str, params: Sequence[Any]) -> tuple[list[str], list[dict[str, Any]]]:
    try:
        with conn.cursor() as cur:
            cur.execute(sql, list(params))
            if cur.description is None:
                return [], []
            cols = [d.name for d in cur.description]
            rows = [dict(r) for r in cur.fetchall()]
            return cols, rows
    except Exception as e:
        raise classify_query_error(e) from e


def classify_query_error(e: Exception) -> CliError:
    sqlstate = getattr(e, "sqlstate", "") or ""
    msg = short_sql_error(str(e))
    low = msg.lower()
    if sqlstate == "42883" or ("does not exist" in low and "function" in low):
        return CliError(
            f"pageinspect 확장 또는 필요한 함수 부재: {msg} "
            "(`CREATE EXTENSION pageinspect;` 및 버전 호환성을 확인하세요.)",
            EXIT_QUERY,
        )
    if sqlstate == "42P01" or ("does not exist" in low and "relation" in low):
        return CliError(f"잘못된 관계 이름: {msg}", EXIT_QUERY)
    if (
        "beyond" in low
        or "out of range" in low
        or "invalid block" in low
        or "block number" in low
    ):
        return CliError(f"범위를 벗어난 블록 번호: {msg}", EXIT_QUERY)
    if sqlstate:
        return CliError(f"SQL 실행 실패 (SQLSTATE {sqlstate}): {msg}", EXIT_QUERY)
    return CliError(f"SQL 실행 실패: {msg}", EXIT_QUERY)


# ---------------------------------------------------------------------------
# 쿼리 팩토리: 단일 SQL 명령은 선언으로 생성한다.
# template의 {fn}·{raw}는 식별자가 아닌 호출 식 치환용이며,
# 사용자 입력은 항상 바인드 파라미터(%s)로만 전달된다.
# ---------------------------------------------------------------------------
RunFn = Callable[[Any, Any], tuple[list[str], list[dict[str, Any]]]]


def raw_query(
    fn: str,
    template: str,
    extra: Callable[[Any], list[Any]] | None = None,
    requires: Sequence[Sequence[str]] | None = None,
) -> RunFn:
    """get_raw_page 기반 단일 조회. 예: "SELECT * FROM {fn}({raw})"."""
    need = [list(g) for g in (requires if requires is not None else (["get_raw_page"], [fn]))]

    def run(conn, a) -> tuple[list[str], list[dict[str, Any]]]:
        ensure_functions(conn, need)
        expr, params = raw_page_call(a.relation, normalize_fork(a.fork), a.block)
        return fetch(conn, template.format(fn=fn, raw=expr), [*params, *(extra(a) if extra else [])])

    run.requires = need  # type: ignore[attr-defined]  # 테스트·문서용 메타데이터
    return run


def direct_query(
    template: str,
    params_of: Callable[[Any], list[Any]],
    requires: Sequence[Sequence[str]],
) -> RunFn:
    """릴레이션 직접 지정형 단일 조회 (get_raw_page를 거치지 않음)."""
    need = [list(g) for g in requires]

    def run(conn, a) -> tuple[list[str], list[dict[str, Any]]]:
        ensure_functions(conn, need)
        return fetch(conn, template, list(params_of(a)))

    run.requires = need  # type: ignore[attr-defined]
    return run


# -- 다단계·폴백이 필요한 명령 (원본 함수 의미 그대로 유지) --------------------
def run_heap_attrs(conn, a) -> tuple[list[str], list[dict[str, Any]]]:
    ensure_functions(conn, [["get_raw_page"], ["heap_page_item_attrs"]])
    expr, params = raw_page_call(a.relation, normalize_fork(a.fork), a.block)
    if a.detoast:
        # detoast 지원 빌드에서는 3인수형, 아니면 2인수형으로 폴백
        try:
            return fetch(
                conn,
                f"SELECT * FROM heap_page_item_attrs(({expr}), %s, TRUE)",
                [*params, a.relation],
            )
        except CliError as ce:
            if "함수 부재" not in str(ce):
                raise
    return fetch(conn, f"SELECT * FROM heap_page_item_attrs(({expr}), %s)", [*params, a.relation])


run_heap_attrs.requires = [["get_raw_page"], ["heap_page_item_attrs"]]  # type: ignore[attr-defined]


def run_heap_split(conn, a) -> tuple[list[str], list[dict[str, Any]]]:
    """tuple_data_split 래퍼: heap_page_items에서 해당 lp 행을 읽어 분리한다."""
    ensure_functions(conn, [["get_raw_page"], ["heap_page_items"], ["tuple_data_split"]])
    expr, params = raw_page_call(a.relation, normalize_fork(a.fork), a.block)
    _, rows = fetch(conn, f"SELECT * FROM heap_page_items(({expr}))", params)
    target = next((r for r in rows if r.get("lp") == a.lp), None)
    if target is None:
        raise CliError(
            f"범위를 벗어난 항목 오프셋: lp={a.lp} (블록 {a.block}에 {len(rows)}개 항목).",
            EXIT_QUERY,
        )
    t_data = target.get("t_data")
    if t_data is None:
        raise CliError(f"lp={a.lp} 행에 t_data가 없습니다 (삭제/미사용 항목일 수 있음).", EXIT_QUERY)
    base = [a.relation, bytes(t_data), target.get("t_infomask"), target.get("t_infomask2"), target.get("t_bits")]
    if a.detoast:
        try:
            _, srows = fetch(
                conn,
                "SELECT tuple_data_split(%s::regclass::oid, %s, %s, %s, %s, TRUE) AS attrs",
                base,
            )
        except CliError as ce:
            if "함수 부재" not in str(ce):
                raise
            _, srows = fetch(
                conn,
                "SELECT tuple_data_split(%s::regclass::oid, %s, %s, %s, %s) AS attrs",
                base,
            )
    else:
        _, srows = fetch(
            conn,
            "SELECT tuple_data_split(%s::regclass::oid, %s, %s, %s, %s) AS attrs",
            base,
        )
    arr = (srows[0].get("attrs") if srows else None) or []
    return ["attnum", "value"], [
        {"attnum": i + 1, "value": bytes(v) if isinstance(v, (bytes, bytearray, memoryview)) else v}
        for i, v in enumerate(arr)
    ]


run_heap_split.requires = [["get_raw_page"], ["heap_page_items"], ["tuple_data_split"]]  # type: ignore[attr-defined]


def run_btree_range_stats(conn, a) -> tuple[list[str], list[dict[str, Any]]]:
    """범위 통계: bt_page_stats를 [start, start+count) 구간에 순차 호출한다."""
    ensure_functions(conn, [["bt_page_stats"]])
    all_cols: list[str] = []
    out: list[dict[str, Any]] = []
    for blk in range(a.start, a.start + a.count):
        cols, rows = fetch(conn, "SELECT * FROM bt_page_stats(%s, %s)", [a.relation, blk])
        if not all_cols:
            all_cols = ["block", *cols]
        for r in rows:
            out.append({"block": blk, **r})
    return all_cols, out


run_btree_range_stats.requires = [["bt_page_stats"]]  # type: ignore[attr-defined]


def run_btree_items(conn, a) -> tuple[list[str], list[dict[str, Any]]]:
    ensure_functions(conn, [["bt_page_items"]])
    try:
        return fetch(conn, "SELECT * FROM bt_page_items(%s, %s)", [a.relation, a.block])
    except CliError as ce:
        if "함수 부재" not in str(ce):
            raise
        # 구버전 bytea형 폴백
        expr, params = raw_page_call(a.relation, normalize_fork(a.fork), a.block)
        return fetch(conn, f"SELECT * FROM bt_page_items(({expr}))", params)


run_btree_items.requires = [["bt_page_items"]]  # type: ignore[attr-defined]


# ---------------------------------------------------------------------------
# 명령 스펙: 파서·디스패치·함수 검사의 단일 진실 공급원
# ---------------------------------------------------------------------------
BLOCK_REQUIRED = "required"  # --block 필수
BLOCK_OPTIONAL = "optional"  # --block 생략 시 0 (메타페이지 관례)
BLOCK_NONE = "none"          # --block 없음


@dataclass(frozen=True)
class Command:
    group: str
    name: str
    help: str
    run: RunFn
    relation: bool = True
    rel_label: str = "검사 대상 테이블/인덱스"
    block: str = BLOCK_REQUIRED
    fork: bool = False
    fork_default: str | None = None
    fork_help: str = "검사할 포크 (main/fsm/vm/init, 기본: main)"
    detoast: bool = False
    extra: str = "none"  # none | lp | infomasks | range
    extra_args: tuple = field(default=())


GROUP_HELP = {
    "page": "페이지 검사 (get_raw_page/page_header/...)",
    "heap": "Heap 검사 (heap_page_items/...)",
    "btree": "B-tree 검사 (bt_metap/bt_page_stats/...)",
    "brin": "BRIN 검사",
    "gin": "GIN 검사",
    "gist": "GiST 검사",
    "hash": "Hash 검사",
}

TABLE_REL = "검사 대상 테이블"
INDEX_REL = "검사 대상 인덱스"
BOTH_REL = "검사 대상 테이블/인덱스"

COMMANDS: list[Command] = [
    # -- page --
    Command("page", "raw", "페이지 원시 데이터 조회 (get_raw_page)",
            raw_query("get_raw_page", "SELECT {raw} AS page", requires=(["get_raw_page"],)),
            rel_label=BOTH_REL, fork=True),
    Command("page", "header", "페이지 헤더 조회 (page_header)",
            raw_query("page_header", "SELECT * FROM {fn}({raw})"),
            rel_label=BOTH_REL, fork=True),
    Command("page", "checksum", "페이지 체크섬 계산 (page_checksum)",
            raw_query("page_checksum", "SELECT {fn}(({raw}), %s) AS checksum", extra=lambda a: [a.block]),
            rel_label=BOTH_REL, fork=True),
    Command("page", "fsm", "FSM 페이지 정보 조회 (fsm_page_contents)",
            raw_query("fsm_page_contents", "SELECT {fn}(({raw})) AS fsm"),
            rel_label=BOTH_REL, fork=True, fork_default="fsm"),
    # -- heap --
    Command("heap", "items", "Heap 튜플 항목 조회 (heap_page_items)",
            raw_query("heap_page_items", "SELECT * FROM {fn}(({raw}))"),
            rel_label=TABLE_REL, fork=True),
    Command("heap", "attrs", "튜플 속성값 조회 (heap_page_item_attrs)",
            run_heap_attrs, rel_label=TABLE_REL, fork=True, detoast=True),
    Command("heap", "split", "튜플 내부 구조 분리 조회 (tuple_data_split)",
            run_heap_split, rel_label=TABLE_REL, fork=True, detoast=True, extra="lp"),
    Command("heap", "flags", "infomask 플래그 해석 (heap_tuple_infomask_flags)",
            direct_query("SELECT * FROM heap_tuple_infomask_flags(%s, %s)",
                         lambda a: [a.infomask, a.infomask2],
                         (["heap_tuple_infomask_flags"],)),
            relation=False, block=BLOCK_NONE, extra="infomasks"),
    # -- btree --
    Command("btree", "meta", "메타페이지 정보 (bt_metap)",
            direct_query("SELECT * FROM bt_metap(%s)", lambda a: [a.relation], (["bt_metap"],)),
            rel_label=INDEX_REL, block=BLOCK_NONE),
    Command("btree", "stats", "지정 페이지 통계 (bt_page_stats)",
            direct_query("SELECT * FROM bt_page_stats(%s, %s)", lambda a: [a.relation, a.block],
                         (["bt_page_stats"],)),
            rel_label=INDEX_REL),
    Command("btree", "range-stats", "페이지 범위 통계 (bt_page_stats 순차 호출)",
            run_btree_range_stats, rel_label=INDEX_REL, block=BLOCK_NONE, extra="range"),
    Command("btree", "items", "페이지 내 인덱스 항목 (bt_page_items)",
            run_btree_items, rel_label=INDEX_REL, fork=True,
            fork_help="검사할 포크 (bytea 폴백용, 기본: main)"),
    # -- brin --
    Command("brin", "type", "페이지 유형 (brin_page_type)",
            raw_query("brin_page_type", "SELECT {fn}(({raw})) AS page_type"),
            rel_label=INDEX_REL, fork=True),
    Command("brin", "meta", "메타페이지 정보 (brin_metapage_info)",
            raw_query("brin_metapage_info", "SELECT * FROM {fn}(({raw}))"),
            rel_label=INDEX_REL, fork=True, block=BLOCK_OPTIONAL),
    Command("brin", "revmap",
            "역매핑 정보 (brin_revmap_data; revmap 블록 번호는 brin meta의 lastrevmappage 참조)",
            raw_query("brin_revmap_data", "SELECT {fn}(({raw})) AS revmap"),
            rel_label=INDEX_REL, fork=True),
    Command("brin", "items", "BRIN 페이지 항목 (brin_page_items)",
            raw_query("brin_page_items", "SELECT * FROM {fn}(({raw}), %s::regclass::oid)",
                      extra=lambda a: [a.relation]),
            rel_label=INDEX_REL, fork=True),
    # -- gin --
    Command("gin", "meta", "GIN 조회 (gin_metapage_info)",
            raw_query("gin_metapage_info", "SELECT * FROM {fn}(({raw}))"),
            rel_label=INDEX_REL, fork=True, block=BLOCK_OPTIONAL),
    Command("gin", "opaque", "GIN 조회 (gin_page_opaque_info)",
            raw_query("gin_page_opaque_info", "SELECT * FROM {fn}(({raw}))"),
            rel_label=INDEX_REL, fork=True),
    Command("gin", "items", "GIN 조회 (gin_leafpage_items)",
            raw_query("gin_leafpage_items", "SELECT * FROM {fn}(({raw}))"),
            rel_label=INDEX_REL, fork=True),
    # -- gist --
    Command("gist", "opaque", "opaque 영역 정보 (gist_page_opaque_info)",
            raw_query("gist_page_opaque_info", "SELECT * FROM {fn}(({raw}))"),
            rel_label=INDEX_REL, fork=True),
    Command("gist", "items", "페이지 항목 (gist_page_items)",
            raw_query("gist_page_items", "SELECT * FROM {fn}(({raw}), %s::regclass::oid)",
                      extra=lambda a: [a.relation]),
            rel_label=INDEX_REL, fork=True),
    Command("gist", "raw-items", "원시 페이지 항목 (gist_page_items_bytea)",
            raw_query("gist_page_items_bytea", "SELECT * FROM {fn}(({raw}))"),
            rel_label=INDEX_REL, fork=True),
    # -- hash --
    Command("hash", "type", "페이지 유형 (hash_page_type)",
            raw_query("hash_page_type", "SELECT {fn}(({raw})) AS page_type"),
            rel_label=INDEX_REL, fork=True),
    Command("hash", "stats", "페이지 통계 (hash_page_stats)",
            raw_query("hash_page_stats", "SELECT * FROM {fn}(({raw}))"),
            rel_label=INDEX_REL, fork=True),
    Command("hash", "items", "페이지 항목 (hash_page_items)",
            raw_query("hash_page_items", "SELECT * FROM {fn}(({raw}))"),
            rel_label=INDEX_REL, fork=True),
    Command("hash", "meta", "메타페이지 정보 (hash_metapage_info)",
            raw_query("hash_metapage_info", "SELECT * FROM {fn}(({raw}))"),
            rel_label=INDEX_REL, fork=True, block=BLOCK_OPTIONAL),
    Command("hash", "bitmap", "비트맵 페이지 정보 (hash_bitmap_info)",
            direct_query("SELECT * FROM hash_bitmap_info(%s::regclass::oid, %s)",
                         lambda a: [a.relation, a.block], (["hash_bitmap_info"],)),
            rel_label=INDEX_REL),
]

SPECS: dict[tuple[str, str], Command] = {(c.group, c.name): c for c in COMMANDS}


# ---------------------------------------------------------------------------
# 출력 (형식은 검사 기능과 독립적)
# ---------------------------------------------------------------------------
def to_jsonable(v: Any) -> Any:
    if isinstance(v, (bytes, bytearray, memoryview)):
        return "\\x" + bytes(v).hex()
    if isinstance(v, (list, tuple)):
        return [to_jsonable(x) for x in v]
    if isinstance(v, dict):
        return {k: to_jsonable(x) for k, x in v.items()}
    return v


def rows_to_jsonable(rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
    return [{k: to_jsonable(val) for k, val in r.items()} for r in rows]


def cell_text(v: Any, max_width: int = 64) -> str:
    """표 셀용 축약: bytea 절단, 멀티라인 한 줄화."""
    if v is None:
        return "NULL"
    if isinstance(v, (bytes, bytearray, memoryview)):
        b = bytes(v)
        s = "\\x" + b.hex()
        if len(s) > max_width:
            return f"{s[: max_width - 20]}... ({len(b)} bytes)"
        return s
    s = str(v).replace("\n", "\\n").replace("\r", "\\r").replace("\t", " ")
    if len(s) > max_width:
        return s[: max_width - 3] + "..."
    return s


def format_table(columns: Sequence[str], rows: list[dict[str, Any]]) -> str:
    if not columns:
        return "(no columns)\n"
    tabulate = import_tabulate()
    body = [[cell_text(r.get(c)) for c in columns] for r in rows]
    out = tabulate(body, headers=list(columns), tablefmt="simple", disable_numparse=True)
    return out + f"\n({len(rows)} row{'s' if len(rows) != 1 else ''})\n"


def render(columns: list[str], rows: list[dict[str, Any]], fmt: str) -> str:
    if fmt == "json":
        return json.dumps(rows_to_jsonable(rows), indent=2, ensure_ascii=False, default=str) + "\n"
    if fmt == "yaml":
        try:
            import yaml  # type: ignore
        except ImportError as e:
            raise CliError(
                "YAML 출력에 PyYAML이 필요합니다. "
                "diagnostics/pageinspect-cli에서 `uv sync`를 실행하세요.",
                EXIT_QUERY,
            ) from e
        return yaml.safe_dump(
            rows_to_jsonable(rows), allow_unicode=True, sort_keys=False, default_flow_style=False
        )
    return format_table(columns, rows)


def emit(text: str, output: str | None, quiet: bool, nrows: int) -> None:
    if output:
        try:
            parent = os.path.dirname(os.path.abspath(output))
            if parent:
                os.makedirs(parent, exist_ok=True)
            with open(output, "w", encoding="utf-8") as f:
                f.write(text)
        except OSError as e:
            raise CliError(f"출력 파일 쓰기 실패 ({output}): {e}", EXIT_OUTPUT) from e
        if not quiet:
            print(f"wrote {nrows} row(s) to {output}", file=sys.stderr)
    else:
        sys.stdout.write(text)


# ---------------------------------------------------------------------------
# 인수 파싱 (COMMANDS 스펙에서 생성)
# ---------------------------------------------------------------------------
def _add_command_parser(subs, cmd: Command) -> None:
    p = subs.add_parser(cmd.name, help=cmd.help)
    if cmd.relation:
        p.add_argument("relation", metavar="RELATION", help=cmd.rel_label)
    if cmd.block == BLOCK_REQUIRED:
        p.add_argument("--block", type=non_negative_int("--block"), required=True,
                       help="검사할 블록 번호 (0 이상)")
    elif cmd.block == BLOCK_OPTIONAL:
        p.add_argument("--block", type=non_negative_int("--block"), default=0,
                       help="검사할 블록 번호 (기본: %(default)s)")
    if cmd.fork:
        p.add_argument("--fork", default=cmd.fork_default, metavar="NAME", help=cmd.fork_help)
    if cmd.detoast:
        p.add_argument("--detoast", action="store_true", help="지원되는 경우 TOAST 데이터 복원")
    if cmd.extra == "lp":
        p.add_argument("--lp", type=positive_int("--lp"), required=True,
                       help="항목 오프셋(lp, 1부터 시작)")
    elif cmd.extra == "infomasks":
        p.add_argument("--infomask", type=int, required=True, help="t_infomask 값")
        p.add_argument("--infomask2", type=int, required=True, help="t_infomask2 값")
    elif cmd.extra == "range":
        p.add_argument("--start", type=non_negative_int("--start"), required=True, help="시작 블록 번호")
        p.add_argument("--count", type=positive_int("--count"), required=True,
                       help=f"처리할 페이지 수 (최대 {MAX_RANGE_COUNT})")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="pageinspect-cli.py",
        description="PostgreSQL pageinspect 확장을 위한 CLI (원본 함수의 인수·반환 보존)",
        epilog=(
            "예시:\n"
            "  uv run pageinspect-cli.py --db 'postgresql://user:pass@localhost:5432/mydb' page header public.my_table --block 0\n"
            "  uv run pageinspect-cli.py --db \"$DB\" --format json -o result.json page header public.my_table --block 0\n"
            "  uv run pageinspect-cli.py --db \"$DB\" heap flags --infomask 2308 --infomask2 2\n"
            "연결 URI는 환경변수 PAGEINSPECT_DB 또는 DATABASE_URL로도 지정할 수 있습니다 (비밀번호는 로그에 노출하지 않음).\n"
            "확장 문법 예시:\n"
            "  --db 'postgresql://u:p@h1:5432,h2:5432/mydb?target_session_attrs=read-write&connect_timeout=5'\n"
            "  --db 'postgresql://u:p@localhost:5432/mydb?options=-c%20search_path%3Dmyschema&sslmode=prefer'\n"
            "  --db 'postgresql://u:p@localhost:5432/mydb?options=-c%20search_path%3Dmyschema%20-c%20timezone%3DUTC'\n"
            "  --db 'postgresql://u:p@localhost:5432/mydb?options=-c%20statement_timeout%3D3000'\n"
            "참고: 연결 URI는 libpq 표준만 따른다. `schema` 같은 ORM 전용 파라미터는 거부되며, "
            "스키마·타임존·타임아웃 지정은 표준 `options` 파라미터를 사용한다 (공백=%20, 등호=%3D로 URL 인코딩)."
        ),
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument(
        "--db",
        dest="db_uri",
        default=None,
        metavar="URI",
        help=(
            "PostgreSQL 연결 URI (예: postgresql://user:pass@host:5432/db). "
            "libpq 표준 쿼리 파라미터 지원: sslmode/sslcert/sslkey/sslrootcert, connect_timeout, "
            "target_session_attrs, application_name, options 등. "
            "다중 호스트(host1,host2) 그대로 전달."
        ),
    )
    parser.add_argument("--format", dest="fmt", choices=VALID_FORMATS, default="table",
                        help="출력 형식 (기본: table)")
    parser.add_argument("-o", "--output", default=None, metavar="FILE", help="결과 파일 지정 (없으면 stdout)")
    parser.add_argument("-q", "--quiet", action="store_true", help="부가 메시지 최소화")
    parser.add_argument("-v", "--verbose", action="store_true", help="상세 실행 정보 출력 (비밀번호 제외)")
    parser.add_argument("-V", "--version", action="version", version=f"%(prog)s {__version__}")

    groups = parser.add_subparsers(dest="group", required=True, metavar="GROUP")
    group_parsers: dict[str, Any] = {}
    for cmd in COMMANDS:
        if cmd.group not in group_parsers:
            group_parsers[cmd.group] = groups.add_parser(cmd.group, help=GROUP_HELP[cmd.group])
            group_parsers[cmd.group + ":subs"] = group_parsers[cmd.group].add_subparsers(
                dest="cmd", required=True, metavar="CMD"
            )
        _add_command_parser(group_parsers[cmd.group + ":subs"], cmd)
    return parser


# ---------------------------------------------------------------------------
# main
# ---------------------------------------------------------------------------
def main(argv: Sequence[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)

    if args.quiet and args.verbose:
        parser.error("--quiet와 --verbose를 함께 사용할 수 없습니다.")

    def log(msg: str) -> None:
        if not args.quiet:
            print(msg, file=sys.stderr)

    def vlog(msg: str) -> None:
        if args.verbose and not args.quiet:
            print(f"[verbose] {msg}", file=sys.stderr)

    key = (args.group, args.cmd)
    spec = SPECS.get(key)
    if spec is None:
        print(f"오류: 지원하지 않는 명령: {args.group} {args.cmd}", file=sys.stderr)
        return EXIT_USAGE

    # 관계/포크/범위 검증 (DB 연결 전)
    try:
        if spec.relation:
            args.relation = validate_relation(args.relation)
        if spec.fork:
            args.fork = normalize_fork(args.fork)
        if spec.extra == "range" and args.count > MAX_RANGE_COUNT:
            parser.error(f"--count는 최대 {MAX_RANGE_COUNT}까지 지정할 수 있습니다.")
    except ValueError as e:
        print(f"오류: {e}", file=sys.stderr)
        return EXIT_USAGE

    db_uri = args.db_uri or os.environ.get("PAGEINSPECT_DB") or os.environ.get("DATABASE_URL")
    if not db_uri:
        print(
            "오류: --db URI가 필요합니다 (또는 환경변수 PAGEINSPECT_DB/DATABASE_URL). "
            "예: --db 'postgresql://user:pass@localhost:5432/mydb'",
            file=sys.stderr,
        )
        return EXIT_USAGE

    vlog(f"DB: {mask_password(db_uri)}")
    vlog(f"command: {args.group} {args.cmd} / format: {args.fmt}")

    try:
        conninfo = prepare_conninfo(db_uri)
    except ValueError as e:
        print(f"오류: {e}", file=sys.stderr)
        return EXIT_USAGE

    try:
        conn = connect_db(conninfo, db_uri)
    except CliError as e:
        print(f"오류: {e}", file=sys.stderr)
        return e.code

    try:
        t0 = time.monotonic()
        num, ver = server_version(conn)
        vlog(f"server_version: {ver} (server_version_num={num})")
        if num < MIN_SERVER_VERSION_NUM:
            log(f"경고: 지원하지 않는 PostgreSQL 버전일 수 있습니다 (server_version={ver}). 일부 함수가 없을 수 있습니다.")
        check_extension(conn, mask_password(db_uri))
        columns, rows = spec.run(conn, args)
        dt = time.monotonic() - t0
        vlog(f"rows: {len(rows)} / elapsed: {dt:.3f}s")
        text = render(columns, rows, args.fmt)
        emit(text, args.output, args.quiet, len(rows))
        return EXIT_OK
    except CliError as e:
        print(f"오류: {e}", file=sys.stderr)
        return e.code
    except BrokenPipeError:
        return EXIT_OK
    except Exception as e:  # 최후 방어선 (비밀번호 노출 방지: URI 원문 출력 금지)
        print(f"오류: 예상치 못한 실패: {short_sql_error(str(e))}", file=sys.stderr)
        return EXIT_QUERY
    finally:
        try:
            conn.close()
        except Exception:
            pass


if __name__ == "__main__":
    sys.exit(main())
