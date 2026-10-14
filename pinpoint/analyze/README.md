# pinpoint/analyze — mini-collector JSONL → 단일 요청 CallStack JSON

`pinpoint/collector/mini-collector-data/*.jsonl`을 읽어
Pinpoint Web의 **CallStack(CallTree) 1개 트랜잭션**을 JSON으로 재현한다.
목적은 `jsonl to json`: 1개 HTTP 요청의 호출 트리를 파일 하나로 뽑아 분석/회귀비교.

## 왜 이렇게 만들었나

mini-collector는 HBase 없이 agent가 쏘는 gRPC를 그대로 JSONL로 저장한다.
그래서 Web UI의 ServerMap/Scatter 없이도, 아래 3종만 있으면 1개 요청은 복원된다:

- `spans.jsonl`: `PSpanMessage{span|spanChunk}` (proto `v1/Span.proto`)
- `metadata.jsonl`: `apiId→apiInfo`, `sqlId→sql`
- `stat.jsonl`: 이번 도구와 무관 (JVM/URI 통계용, 읽지 않음)

Pinpoint 원본(`github.com/pinpoint-apm/pinpoint@master`) 매핑:

| 이 도구 | 원본 |
|---|---|
| `group_by_transaction` (tx+spanId 묶음) | `NodeList` — 같은 spanId의 span+chunk를 1 Node로 |
| `collect_aligns` (sequence 정렬) | `Node.buildAlignList` + `mergeAndSort` + `AlignComparator(=SpanEventComparator: sequence순)` |
| proto 기본값 복원 (`sequence` 누락=0, `depth` 누락=-1) | `SpanEventBo: sequence=0, depth=-1`, `SpanCallTree: LEVEL_DEPTH=-1, ROOT_DEPTH=0` |
| `build_call_tree` | `SpanCallTree.add(depth, align)` — 형제/자식/조상 + `hasCorrupted`(sequence 불연속) |
| `build_records` | `RecordFactory.get` — id/parentId/depth/api/argument/시간/예외/annotation |
| annotation/serviceType 이름 | `AnnotationKey.java` (20=SQL-ID, 46=http.status…), `ServiceType.java` (1010 TOMCAT, 5051 MVC, 2500 PG…) |

분산 추적(link/fill/unknown-span)과 비동기 분리(`SpanAsyncEventMap`)는
단일 노드(dev)에서는 필요 없어 표시(`async:true`)만 하고 함께 정렬한다.

## 사용

```bash
uv run pinpoint/analyze/analyze.py --help   # 전체 설명 (개념·옵션·예시)

# stats: 구간 요약 (URI 집계 + JVM)
uv run pinpoint/analyze/analyze.py stats url
uv run pinpoint/analyze/analyze.py stats url --sort max --limit 5
uv run pinpoint/analyze/analyze.py stats jvm

# list: 찾기 → 좁히기 (노이즈 제외, 느린 순, 상태 필터)
uv run pinpoint/analyze/analyze.py list --rpc orders --sort elapsed
uv run pinpoint/analyze/analyze.py list --status 500

# trace: 번호 직접 지정 또는 조건으로 선택 (형식은 하위 서브커맨드, 생략 불가)
uv run pinpoint/analyze/analyze.py trace --tx 42 tree
uv run pinpoint/analyze/analyze.py trace --rpc orders --status 200 --pick latest json
uv run pinpoint/analyze/analyze.py trace --rpc orders --pick slowest tree   # elapsed 최대
uv run pinpoint/analyze/analyze.py trace --rpc orders --pick first tree     # 최초 1건

# trace 형식: tree(사람용) / json / yaml (json·yaml은 항상 전체, 자름 없음)
uv run pinpoint/analyze/analyze.py trace --tx 42 json > /tmp/tx42.json
uv run pinpoint/analyze/analyze.py trace --tx 42 yaml | head -n 20
uv run pinpoint/analyze/analyze.py trace --tx 42 json | jq '.records | length'

# trace tree 출력조건: 함수명·SQL 표시 방식
uv run pinpoint/analyze/analyze.py trace --tx 42 tree --sql full    # :: SQL 전문 표시
uv run pinpoint/analyze/analyze.py trace --tx 42 tree --name full   # 풀패키지(class.method)로
uv run pinpoint/analyze/analyze.py trace --tx 42 tree --sql hide    # :: SQL 숨기기
```

축: 전역 원본위치(`--data-dir`, 액션 앞에) / 조건식(list: `--rpc/--status`,
trace: `--tx/--rpc/--status/--pick`. 없으면 전체·수평 AND) /
형식(trace 하위 서브커맨드, 택1·생략 불가: `tree|json|yaml`,
json·yaml은 항상 full) / tree 출력조건(`--name short|full`, `--sql show|hide|full`).
형식이 구조라서 scope 어긋남이 생기지 않는다.

이전 옵션 → 현재: `show`→`trace`, `X`→하위 서브커맨드 `X`,
`--tree`→`trace ... tree`, `--json/--yaml`→`trace ... json/yaml`,
`--full-name`→`--name full`, `--no-sql`→`--sql hide`, `--full`→`--sql full`,
`--compact`/`--json compact`→삭제(json은 항상 compact 한 줄).
별명: `list=ls`, `trace=tr`.

## 상황별 예시

느린 요청부터 볼 때 (병목 헌팅):

```bash
uv run pinpoint/analyze/analyze.py list --sort elapsed --limit 5
uv run pinpoint/analyze/analyze.py trace --rpc orders --pick slowest tree
```

트리에서 `elapsedMs`+퍼센트가 큰 가지가 병목이다.
DB·외부연동이 의심되면 트리의 `:: SQL`(`--sql full`로 전문)을 읽는다.

실패만 추릴 때 (장애 트리아지):

```bash
uv run pinpoint/analyze/analyze.py list --status 500
uv run pinpoint/analyze/analyze.py trace --rpc orders --status 500 --pick latest json
```

트리에서 예외 노드 뒤에 `!! 메시지`가 붙어 위치를 알 수 있고,
기계적으로 뽑을 땐 `trace --tx 42 json | jq '[.records[] | select(.exception)]'`.

DB 쿼리만 리뷰할 때:

```bash
uv run pinpoint/analyze/analyze.py trace --tx 42 tree | grep "::"
uv run pinpoint/analyze/analyze.py trace --tx 42 tree --sql full | grep "::"   # 긴 SQL 전문 보기
```

트리 줄에 `:: SQL`로 호출 순서대로 붙어 나오며, 120자로 잘리니 전문은 `--sql full`로 본다.

캐시 동작 확인할 때:

```bash
uv run pinpoint/analyze/analyze.py trace --tx 42 tree | grep -i -E "redis|lettuce|cache"
```

`serviceTypeName`이 `REDIS_LETTUCE` 등으로 표시되므로 트리에서 grep하면
캐시 조회/무효화(`unlink` 등)가 어느 depth에서 일어났는지 보인다.

배포 전후 비교할 때 (회귀 확인):

```bash
uv run pinpoint/analyze/analyze.py trace --rpc orders --pick slowest json > /tmp/before.json
# ... 수정·재배포·재수집 후 ...
uv run pinpoint/analyze/analyze.py trace --rpc orders --pick slowest json > /tmp/after.json
diff <(jq '.records | length' /tmp/before.json) <(jq '.records | length' /tmp/after.json)
```

JSON으로 저장해두면 `jq`/`diff`로 구조·건수·시간을 기계 비교할 수 있다.

JSON을 직접 파볼 때:

```bash
uv run pinpoint/analyze/analyze.py trace --tx 42 json | jq '.records | length'
uv run pinpoint/analyze/analyze.py trace --tx 42 json | jq '[.records[] | select(.exception)]'
uv run pinpoint/analyze/analyze.py trace --tx 42 json | jq '[.records[] | select(.serviceType == 2500)] | length'
```

JSON을 직접 파볼 땐 `| jq`로 바로 받는다 (json은 항상 compact 한 줄).

저장과 보기를 함께 할 때 (`tee`):

```bash
uv run pinpoint/analyze/analyze.py trace --tx 42 tree | tee /tmp/tx42.txt
```

저장은 셸 리다이렉션(`>`, `tee`)으로 한다. 별도 저장 옵션은 없다.

## 출력 JSON

```jsonc
{
  "transactionId": {"agentId": "...", "agentStartTime": "...", "sequence": "19", "spanId": "..."},
  "rpc": "<request-rpc>",
  "httpStatus": 201,          // span annotation key=46
  "elapsedMs": 113,
  "eventCount": 19, "chunkCount": 0,
  "warnings": ["sequence gap…", "depth jump…"],  // Pinpoint의 corrupted에 해당
  "records": [
    {"id": 1, "parentId": null, "depth": 0, "kind": "root", "title": "<request-rpc>", ...},
    {"id": 2, "parentId": 1, "depth": 1, "sequence": 0, "title": "invoke",
     "api": "org.apache...StandardHostValve.invoke...", "serviceTypeName": "TOMCAT_METHOD",
     "argument": "SQL 본문 또는 annotation 요약", "destinationId": "db명",
     "startTimeMs": 1791991007938, "elapsedMs": 113,
     "exception": {"message": "..."} | null,
     "annotations": [{"key": 20, "name": "SQL-ID", "value": "..."}],
     "async": false, "corrupted": ""}
  ]
}
```

`records`는 전위순회(부모→자식)이며 `id/parentId`로 트리 복원,
`depth`로 들여쓰기, `sequence`로 호출 순서 확인이 가능하다.

## 현재 데이터 예시

- `tx 12~17`: 400, events 2 — 파싱 실패(서비스 진입 전)
- `tx 18`: 400 + `exception: "Details count does not match…"` — 비즈니스 검증 실패
- `tx 19`: 201, 19 events — `single_game insert → detail insert → Redis unlink` 정상
- `tx 20`: analysis 145ms, 59 events + chunk 2 — percentile/rank 쿼리군

## 한계

- 단일 spanId만 지원 (MSA 분산 링크는 `link()/fill()` 미구현)
- 시간은 ms 정밀도 (`startTime + startElapsed`, `elapsed = endElapsed - startElapsed`)
- `gap/execution`은 Pinpoint식 상세 계산 대신 offset 원값(`startOffsetMs/endOffsetMs`) 제공
