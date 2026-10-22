# pageinspect-cli

PostgreSQL `pageinspect` 확장을 위한 단일 파일 CLI (`pageinspect-cli.py`).

기준: PostgreSQL 17의 `pageinspect` 함수 목록. 원본 함수의 인수·반환 필드를 보존한다.

## 의존성

- `psycopg[binary]` — DB 연결 (필수)
- `pyyaml` — YAML 출력 (필수)
- `tabulate` — 표 출력 (필수)
- `pytest` — 테스트 (dev 그룹)

```bash
uv sync            # 필수 + dev 의존성 설치
uv run pytest      # 회귀 테스트 (23개)
PAGEINSPECT_TEST_DB='postgresql://...' uv run pytest  # 실DB 테스트 포함
```

## 구조 (단일 파일)

- `COMMANDS` — 27개 명령의 단일 스펙 (파서·디스패치·필요 함수 검사가 여기서 생성)
- 단일 SQL 명령은 `raw_query`/`direct_query` 팩토리로 선언, 다단계 명령(`heap split`,
  `btree range-stats` 등)만 전용 실행 함수로 구현
- 각 실행 함수의 `requires` 메타데이터로 필요 pageinspect 함수를 사전 점검

## 실행

```bash
cd diagnostics/pageinspect-cli

uv run pageinspect-cli.py --help
uv run pageinspect-cli.py --version

uv run pageinspect-cli.py \
  --db 'postgresql://user:password@localhost:5432/mydb' \
  page header public.my_table --block 0

uv run pageinspect-cli.py \
  --db 'postgresql://user:password@localhost:5432/mydb' \
  --format json -o result.json \
  page header public.my_table --block 0
```

연결 URI는 환경변수 `PAGEINSPECT_DB` 또는 `DATABASE_URL`로도 지정 가능.
비밀번호는 로그·오류 출력에서 마스킹된다.

## 연결 URI 문법 (`--db`) — libpq 표준 준수

```text
postgresql://user:pass@host1:5432,host2:5433/db?param1=v1&param2=v2
```

URI를 파싱·변조하지 않고 libpq에 그대로 전달하는 것이 원칙이다.
`schema` 같은 ORM 전용 비표준 파라미터는 받지 않고, 연결 전에 중단하며 표준 대안을 안내한다.

| 구분 | 파라미터 | 예시 |
|---|---|---|
| 보안/SSL | `sslmode`, `sslcert`, `sslkey`, `sslrootcert` | `?sslmode=require` |
| 네트워크/HA | `connect_timeout`, `target_session_attrs`, `keepalives` | `?target_session_attrs=read-write` |
| 세션/앱 | `application_name`, `options` | `?application_name=myapp` |
| 다중 호스트 | 콤마 구분 그대로 전달 | `@h1:5432,h2:5432/db` |

핵심은 `options` (공백=`%20`, 등호=`%3D`로 URL 인코딩):

```bash
--db 'postgresql://u:p@localhost:5432/mydb?options=-c%20search_path%3Dmyschema'
--db 'postgresql://u:p@localhost:5432/mydb?options=-c%20search_path%3Dmyschema%20-c%20timezone%3DUTC'
--db 'postgresql://u:p@localhost:5432/mydb?options=-c%20statement_timeout%3D3000'
--db 'postgresql://u:p@h1:5432,h2:5432/mydb?target_session_attrs=read-write&connect_timeout=5'
```

- `connect_timeout`(기본 10초)과 `application_name`(기본 `pageinspect-cli`)은
  URI에 없을 때만 기본값이 부여되며, 사용자 지정이 항상 우선한다.
- `host=.. dbname=..` 키워드 형식도 지원.
- 주의: 쿼리스트링 재조립 시 `%20`을 `+`로 바꾸지 않는다.
  libpq는 `+`를 문자 그대로 취급하므로, `+`로 바뀌면 `FATAL: unrecognized configuration
  parameter "+search_path"`가 난다 (회귀 테스트로 고정).

## view (시각화)

`view`는 새 검사 명령이 아니라 부모와 동일한 조회를 다르게 표현하는 하위 명령이다.
지원하는 명령에만 있으며, 출력은 텍스트 다이어그램으로 고정 (`--format`과 무관, `-o` 저장 가능).

```text
page
  raw          view [layout, hex]
  fsm          view [layout]        # --count로 FSM 블록 범위 스캔
heap
  items        view [layout]
btree
  range-stats  view [chart]         # --field 수치 선택 (기본 live_items)
  items        view [layout]
brin
  revmap       view [map]
hash
  stats        view [chart]         # 단일 페이지 수치 항목별 막대
  items        view [layout]
  bitmap       view [bitmap]        # overflow 페이지가 있을 때만 (없으면 조회 오류 전달)
```

```bash
pageinspect-cli.py --db "$DB" page raw public.t --block 0 view layout
pageinspect-cli.py --db "$DB" btree range-stats idx --start 0 --count 8 view chart --field free_size
pageinspect-cli.py --db "$DB" brin revmap idx --block 1 view map
```

호출 순서 고정: `<부모 명령 인수> view KIND [KIND 옵션]` — 부모 인수(RELATION·--block 등)는
반드시 `view` 앞에 온다. kind 뒤에 부모 인수를 붙이면 오류이다.

나머지 18개 명령(header/checksum/attrs/split/flags/meta/stats/type/opaque/items 등)은
표 출력이 충분하거나 반환값만으로 구조를 확정할 수 없어 `view`를 두지 않는다.
전체 계층은 `--help` / `<그룹> --help` / `<명령> --help`에서 확인 (스펙에서 자동 생성).

## 테스트

`tests/test_pageinspect_cli.py` — 단위(URI·마스킹·출력) + 스텁 DB(핸들러·폴백·바인딩 순서)
+ 환경변수 `PAGEINSPECT_TEST_DB` 지정 시 실DB(함수 존재·page header) 검증.

## 사전 요구사항

연결 대상 DB에 `pageinspect` 확장이 없으면 실행 전에 오류로 중단한다:

```text
오류: 데이터베이스 'mydb'에 'pageinspect' 확장이 설치되어 있지 않습니다.
해당 DB에 권한 있는 사용자로 `CREATE EXTENSION pageinspect;`를 실행한 뒤 다시 시도하세요.
```

## 명령 트리

```text
page   raw | header | checksum | fsm
heap   items | attrs [--detoast] | split --lp N [--detoast] | flags --infomask N --infomask2 M
btree  meta | stats --block N | range-stats --start N --count N | items --block N
brin   type | meta [--block N] | revmap --block N | items --block N
gin    meta [--block N] | opaque --block N | items --block N
gist   opaque | items | raw-items   (각 --block N)
hash   type | stats | items | meta [--block N] | bitmap --block N
```

공통: `--db URI`, `--format table|json|yaml`, `-o FILE`, `-q/--quiet`, `-v/--verbose`, `-V/--version`.
`--fork main|fsm|vm|init`는 `get_raw_page` 기반 명령에서 지원 (`page fsm`은 기본 `fsm`).

## 참고

- `heap split`은 `heap_page_items`에서 해당 `lp` 행을 읽어 `tuple_data_split`을 호출한다.
- `btree range-stats`는 `bt_page_stats`를 `[start, start+count)` 구간에 순차 호출한다 (`--count` 최대 10000).
- `brin revmap`은 `brin_revmap_data`를 호출한다. revmap 페이지 번호는 `brin meta`의
  `lastrevmappage`에서 확인한다 (메타페이지인 블록 0에는 사용 불가).
- 함수가 없으면 `CREATE EXTENSION pageinspect;` 여부와 PostgreSQL 버전을 안내한다.
