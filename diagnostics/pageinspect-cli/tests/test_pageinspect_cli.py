"""pageinspect-cli 회귀 테스트.

실행: diagnostics/pageinspect-cli 에서 `uv run pytest`
실DB 테스트: PAGEINSPECT_TEST_DB='postgresql://...' uv run pytest
"""

import importlib.util
import json
import os
from pathlib import Path
from types import SimpleNamespace

import pytest

CLI_PATH = Path(__file__).parent.parent / "pageinspect-cli.py"


def load_cli():
    import sys

    spec = importlib.util.spec_from_file_location("pageinspect_cli", CLI_PATH)
    mod = importlib.util.module_from_spec(spec)
    sys.modules["pageinspect_cli"] = mod  # dataclass 처리에 필요
    spec.loader.exec_module(mod)
    return mod


cli = load_cli()

EXPECTED_TREE = {
    "page": ["raw", "header", "checksum", "fsm"],
    "heap": ["items", "attrs", "split", "flags"],
    "btree": ["meta", "stats", "range-stats", "items"],
    "brin": ["type", "meta", "revmap", "items"],
    "gin": ["meta", "opaque", "items"],
    "gist": ["opaque", "items", "raw-items"],
    "hash": ["type", "stats", "items", "meta", "bitmap"],
}


# ---------------------------------------------------------------- 스텁 DB
class PgError(Exception):
    def __init__(self, msg, sqlstate=""):
        super().__init__(msg)
        self.sqlstate = sqlstate


class Col:
    def __init__(self, name):
        self.name = name


class FakeCursor:
    def __init__(self, stub):
        self.stub = stub
        self.description = None
        self._rows = []

    def __enter__(self):
        return self

    def __exit__(self, *a):
        return False

    def execute(self, sql, params=None):
        self.stub.calls.append((sql, list(params) if params else []))
        cols, rows = self.stub.route(sql, params or [])
        self.description = [Col(c) for c in cols] if cols else None
        self._rows = rows

    def fetchall(self):
        return self._rows

    def fetchone(self):
        return self._rows[0] if self._rows else None


class FakeConn:
    def __init__(self, stub):
        self.stub = stub

    def cursor(self):
        return FakeCursor(self.stub)

    def close(self):
        pass


class Stub:
    """route 우선순위: 먼저 등록한 on()이 pg_proc 기본 응답보다 앞선다."""

    def __init__(self):
        self.calls = []
        self.handlers = [
            (lambda sql, p: "pg_proc" in sql, self._all_funcs),
        ]

    def _all_funcs(self, sql, params):
        names = params[0] if params else []
        return (["proname"], [{"proname": n} for n in names])

    def on(self, needle, result):
        self.handlers.insert(0, (lambda sql, p, n=needle: n in sql, result))
        return self

    def route(self, sql, params):
        for pred, res in self.handlers:
            if pred(sql, params):
                return res(sql, params) if callable(res) else res
        raise AssertionError(f"unstubbed SQL: {sql}")


def run_handler(spec_key, args, stub):
    return cli.SPECS[spec_key].run(FakeConn(stub), args)


# ---------------------------------------------------------------- 스펙/파서
def test_command_tree_matches_design():
    actual = {}
    for c in cli.COMMANDS:
        actual.setdefault(c.group, []).append(c.name)
    assert actual == EXPECTED_TREE
    assert len(cli.COMMANDS) == 27
    assert len(cli.SPECS) == 27


def test_every_run_has_requires_metadata():
    for c in cli.COMMANDS:
        assert getattr(c.run, "requires", None), f"{c.group} {c.name}에 requires 메타데이터 없음"


def _minimal_argv(cmd):
    argv = [cmd.group, cmd.name]
    if cmd.relation:
        argv.append("public.t")
    if cmd.block == cli.BLOCK_REQUIRED:
        argv += ["--block", "0"]
    if cmd.fork:
        pass  # 기본값 검증은 별도
    if cmd.detoast:
        pass
    if cmd.extra == "lp":
        argv += ["--lp", "1"]
    elif cmd.extra == "infomasks":
        argv += ["--infomask", "1", "--infomask2", "2"]
    elif cmd.extra == "range":
        argv += ["--start", "0", "--count", "1"]
    return argv


def test_all_commands_parse():
    parser = cli.build_parser()
    for cmd in cli.COMMANDS:
        ns = parser.parse_args(_minimal_argv(cmd))
        assert (ns.group, ns.cmd) == (cmd.group, cmd.name)


def test_block_and_fork_defaults():
    parser = cli.build_parser()
    assert parser.parse_args(["brin", "meta", "public.t"]).block == 0
    assert parser.parse_args(["gin", "meta", "public.t"]).block == 0
    assert parser.parse_args(["hash", "meta", "public.t"]).block == 0
    assert parser.parse_args(["page", "fsm", "public.t", "--block", "0"]).fork == "fsm"
    assert parser.parse_args(["page", "header", "public.t", "--block", "0"]).fork is None


def test_range_count_cap():
    with pytest.raises(SystemExit) as e:
        cli.main(["btree", "range-stats", "idx", "--start", "0", "--count", "10001"])
    assert e.value.code == cli.EXIT_USAGE


def test_quiet_verbose_conflict():
    with pytest.raises(SystemExit) as e:
        cli.main(["-q", "-v", "page", "header", "t", "--block", "0"])
    assert e.value.code == cli.EXIT_USAGE


def test_missing_db_returns_usage(monkeypatch):
    monkeypatch.delenv("PAGEINSPECT_DB", raising=False)
    monkeypatch.delenv("DATABASE_URL", raising=False)
    assert cli.main(["page", "header", "public.t", "--block", "0"]) == cli.EXIT_USAGE


# ---------------------------------------------------------------- URI
def test_mask_password():
    assert cli.mask_password("postgresql://u:s3cret@h/db") == "postgresql://u:***@h/db"
    assert cli.mask_password("postgresql://u:s3cret@h1:5432,h2:5433/db?sslmode=require") == \
        "postgresql://u:***@h1:5432,h2:5433/db?sslmode=require"
    assert "mjupw" not in cli.mask_password("host=h dbname=d user=u password=mjupw")
    assert cli.mask_password("") == ""


def test_prepare_conninfo_defaults_and_override():
    c = cli.prepare_conninfo("postgresql://u:p@localhost:5432/mydb")
    assert "connect_timeout=10" in c and "application_name=pageinspect-cli" in c
    c = cli.prepare_conninfo("postgresql://u:p@h/db?connect_timeout=30&application_name=x")
    assert "connect_timeout=30" in c and "connect_timeout=10" not in c and "application_name=x" in c


def test_prepare_conninfo_passthrough_and_multihost():
    c = cli.prepare_conninfo(
        "postgresql://u:p@h1:5432,h2:5433/db?target_session_attrs=read-write"
        "&options=-c%20search_path%3Dmyschema&sslmode=require"
    )
    assert "h1:5432,h2:5433" in c and "target_session_attrs=read-write" in c
    assert "options=" in c and "sslmode=require" in c


def test_options_roundtrip_preserves_percent_encoding():
    # 회귀: %20이 +로 바뀌면 libpq가 '+search_path'를 그대로 넘겨 FATAL이 난다
    c = cli.prepare_conninfo(
        "postgresql://u:p@h/db?options=-c%20search_path%3Dmyschema%20-c%20timezone%3DUTC"
    )
    assert "options=-c%20search_path%3Dmyschema%20-c%20timezone%3DUTC" in c


def test_prepare_conninfo_rejects_nonstandard_schema():
    # libpq 표준이 아닌 ORM 확장 `schema`는 연결 전 차단 + 표준 대안 안내
    with pytest.raises(ValueError) as e:
        cli.prepare_conninfo("postgresql://u:p@h/db?schema=myschema")
    assert "options" in str(e.value) and "search_path" in str(e.value)
    with pytest.raises(ValueError):
        cli.prepare_conninfo("host=localhost dbname=mydb schema=app")


def test_prepare_keyword_conninfo_defaults():
    c = cli.prepare_conninfo("host=localhost dbname=mydb user=u password=p")
    assert "connect_timeout=10" in c and "application_name=pageinspect-cli" in c


# ---------------------------------------------------------------- 출력
def test_json_yaml_table_render():
    rows = [{"a": 1, "b": b"\x01\x02", "c": [b"\x03"], "d": None}]
    cols = ["a", "b", "c", "d"]
    data = json.loads(cli.render(cols, rows, "json"))
    assert data[0]["b"] == "\\x0102" and data[0]["c"] == ["\\x03"]
    y = cli.render(cols, rows, "yaml")
    assert "a: 1" in y
    t = cli.render(cols, rows, "table")
    assert "a" in t and "(1 row)" in t
    assert cli.render([], [], "table") == "(no columns)\n"


def test_cell_text_truncation():
    assert cli.cell_text(b"\x00" * 100).endswith("(100 bytes)")
    assert "\\n" in cli.cell_text("a\nb")
    assert cli.cell_text(None) == "NULL"


def test_emit_file_and_error(tmp_path):
    target = tmp_path / "sub" / "r.json"
    cli.emit("{}", str(target), True, 0)
    assert target.read_text() == "{}"
    with pytest.raises(cli.CliError) as e:
        cli.emit("x", "/proc/nope/f.txt", True, 0)
    assert e.value.code == cli.EXIT_OUTPUT


# ---------------------------------------------------------------- 스텁 핸들러
def test_heap_split_and_range_error():
    stub = Stub().on("heap_page_items", (
        ["lp", "t_data", "t_infomask", "t_infomask2", "t_bits"],
        [{"lp": 1, "t_data": b"\x11", "t_infomask": 1, "t_infomask2": 2, "t_bits": None}],
    )).on("tuple_data_split", (["attrs"], [{"attrs": [b"aa", b"bb"]}]))
    cols, rows = run_handler(("heap", "split"),
                             SimpleNamespace(relation="t", fork=None, block=0, lp=1, detoast=False), stub)
    assert (cols, rows) == (["attnum", "value"],
                            [{"attnum": 1, "value": b"aa"}, {"attnum": 2, "value": b"bb"}])
    with pytest.raises(cli.CliError):
        run_handler(("heap", "split"),
                    SimpleNamespace(relation="t", fork=None, block=0, lp=9, detoast=False), stub)


def test_range_stats_iterates_blocks():
    seen = []

    def rt(sql, params):
        seen.append(params[-1])
        return (["live"], [{"live": 1}])

    stub = Stub()
    stub.handlers.insert(0, (lambda sql, p: "bt_page_stats" in sql, rt))
    cols, rows = run_handler(("btree", "range-stats"),
                             SimpleNamespace(relation="i", start=5, count=3), stub)
    assert seen == [5, 6, 7] and [r["block"] for r in rows] == [5, 6, 7]
    assert cols[0] == "block"


def test_checksum_bind_order():
    stub = Stub().on("page_checksum", (["checksum"], [{"checksum": 1}]))
    run_handler(("page", "checksum"),
                SimpleNamespace(relation="t", fork="fsm", block=7), stub)
    assert stub.calls[-1][1] == ["t", "fsm", 7, 7]


def test_heap_attrs_detoast_fallback():
    def route(sql, params):
        if "heap_page_item_attrs" in sql and "TRUE" in sql:
            raise PgError('function heap_page_item_attrs(bytea, unknown, boolean) does not exist', "42883")
        if "heap_page_item_attrs" in sql:
            return (["att"], [{"att": 1}])
        raise AssertionError(sql)

    stub = Stub()
    stub.handlers.insert(0, (lambda sql, p: "heap_page_item_attrs" in sql, route))
    cols, rows = run_handler(("heap", "attrs"),
                             SimpleNamespace(relation="t", fork=None, block=0, detoast=True), stub)
    assert rows == [{"att": 1}]


def test_btree_items_bytea_fallback():
    def route(sql, params):
        if "bt_page_items(%s, %s)" in sql:
            raise PgError("function bt_page_items(unknown, integer) does not exist", "42883")
        if "bt_page_items" in sql:
            return (["item"], [{"item": 1}])
        raise AssertionError(sql)

    stub = Stub()
    stub.handlers.insert(0, (lambda sql, p: "bt_page_items" in sql, route))
    _, rows = run_handler(("btree", "items"),
                          SimpleNamespace(relation="i", fork=None, block=1), stub)
    assert rows == [{"item": 1}]


def test_brin_revmap_uses_revmap_data():
    stub = Stub().on("brin_revmap_data", (["revmap"], [{"revmap": "(2,1)"}]))
    _, rows = run_handler(("brin", "revmap"),
                          SimpleNamespace(relation="b", fork=None, block=1), stub)
    assert rows == [{"revmap": "(2,1)"}]
    assert "brin_revmap_data" in stub.calls[-1][0]


# ---------------------------------------------------------------- 실DB (게이트)
LIVE_DB = os.environ.get("PAGEINSPECT_TEST_DB")
needs_live = pytest.mark.skipif(not LIVE_DB, reason="PAGEINSPECT_TEST_DB 미설정 시 생략")


@needs_live
def test_live_required_functions_exist_on_pg17():
    conninfo = cli.prepare_conninfo(LIVE_DB)
    conn = cli.connect_db(conninfo, LIVE_DB)
    try:
        wanted = sorted({fn for spec in cli.COMMANDS for grp in spec.run.requires for fn in grp})
        missing = set(wanted) - cli.functions_present(conn, wanted)
        assert not missing, f"PG에 없는 함수: {sorted(missing)}"
    finally:
        conn.close()


@needs_live
def test_live_page_header_json(tmp_path):
    out = tmp_path / "h.json"
    rc = cli.main(["--db", LIVE_DB, "--format", "json", "-o", str(out),
                   "page", "header", "public.member", "--block", "0"])
    assert rc == cli.EXIT_OK
    data = json.loads(out.read_text())
    assert data and "lsn" in data[0]
