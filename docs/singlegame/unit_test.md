# 싱글 게임 단위 테스트 명세서

본 문서는 싱글 게임 서비스 및 피드백 엔진(`SingleGameFeedbackEngine`)의 단위 테스트 사양을 명확히 요약합니다.

---

## 1. 도메인 유효성 검증
- **과목 수 (`totalCourses`)**: `1`, `3`, `6`, `7`, `8`만 허용 (`0`, `2`, `4`, `5`, `9` 등 거부)
- **완료 여부 (`isCompleted`) 정합성**: `isCompleted = true` 시 `details.size() == totalCourses`, `false` 시 `details.size() < totalCourses`
- **반응 속도 범위**: `tEnterMain`, `tClickCourse`, `tClickYes`, `tClickOk` 모두 `1ms` ~ `60,000ms` 범위 내 준수

---

## 2. Base Metrics 연산 정합성
- **과목 처리 시간 ($T_i$)**: $T_i = \text{tClickCourse}_i + \text{tClickYes}_i + \text{tClickOk}_i$
- **`entrySpeed`**: `tEnterMain` 값 그대로 (전체 1회)
- **`aimSpeed`**: `tClickCourse` 배열의 평균값 (N회)
- **`confirmSpeed`**: `tClickYes` 배열의 평균값 (N회)
- **`completeSpeed`**: `tClickOk` 배열의 평균값 (N회)
- **`totalTime`**: `tEnterMain` + 모든 과목의 $T_i$ 합계 (의도적 지연 제외)

---

## 3. Derived Insights 연산 정합성 ($N \ge 3$일 때만 산출, 미만 시 `null`)
- **`initialSprint`**: $T_1 - \frac{1}{N-1} \sum_{i=2}^{N} T_i$ (ms)
- **`paceDeviation`**: $T_i$ 모표준편차 $\sigma$ (ms)
- **`fatigueIndex`**: $\bar{T}_{\text{second\_half}} - \bar{T}_{\text{first\_half}}$ (ms, 홀수 $N$ 시 중앙 과목 제외)
- **`bestPhase`**: `entrySpeed`, `aimSpeed`, `confirmSpeed`, `completeSpeed` 중 percentile 가장 낮은(우수한) 항목
- **`weakestPhase`**: `entrySpeed`, `aimSpeed`, `confirmSpeed`, `completeSpeed` 중 percentile 가장 높은(취약한) 항목

---

## 4. Grade 산출 정합성 (percentile 기반)
| 등급 | percentile 범위 |
|------|----------------|
| S | ≤ 5 |
| A | 5 < x ≤ 30 |
| B | 30 < x < 70 |
| C | 70 ≤ x < 95 |
| D | ≥ 95 |

---

## 5. 피드백 엔진 규칙 (`SingleGameFeedbackEngine`)

각 축은 독립적으로 평가되며, 축 내에서 가장 먼저 만족하는 **단 1개의 코드**만 반환한다.

#### 5-1. Physical Axis (피지컬 밸런스)

| 우선순위 | 코드 | 판정 조건 |
|---|---|---|
| 1 | `GOD_TIER_PHYSICAL` | $\text{aimP} \le 30 \land \text{burstP} \le 30$ |
| 2 | `PHYSICAL_UPGRADE_NEEDED` | $\text{aimP} \ge 70 \land \text{burstP} \ge 70$ |
| 3 | `FAST_BUT_INACCURATE` | $\text{aimP} \ge 70 \land \text{burstP} \le 30$ |
| 4 | `SLOW_AIM` | 위 1~3에 해당하지 않으며 $\text{aimP} > \text{burstP}$ |
| 5 | `SLOW_BURST` | 위 1~4에 해당하지 않는 나머지 |

#### 5-2. Entry & Start Axis (진입 및 초반)

| 우선순위 | 코드 | 판정 조건 |
|---|---|---|
| 1 | `PERFECT_ENTRY_START` | $\text{eP} \le 30 \land \text{startP} \le 30$ |
| 2 | `ENTRY_MASTER_START_NOVICE` | $\text{eP} \le 30 \land \text{startP} \ge 70$ |
| 3 | `ENTRY_LATE_START_MASTER` | $\text{eP} \ge 70 \land \text{startP} \le 30$ |
| 4 | `NEED_FASTER_ENTRY` | $\text{eP} \ge 70 \land \text{startP} > 30$ |
| 5 | `START_HESITATION` | 위 1~4에 해당하지 않는 나머지 |

#### 5-3. Pace & Focus Axis (페이스 및 멘탈 제어, $N \ge 3$일 때만 활성화)

| 우선순위 | 코드 | 판정 조건 |
|---|---|---|
| 1 | `MACHINE_LIKE_PACE` | $\text{paceP} \le 30$ |
| 2 | `EASY_PANIC` | 가짜 지연 직후 과목 $i$에서 $T_i > \bar{T} + 1.5\sigma$ 발생 |
| 3 | `STRONG_FINISHER` | $\bar{T}_{\text{first}} - \bar{T}_{\text{second}} \ge 100\text{ms}$ (홀수 $N$ 시 중앙 과목 제외) |
| 4 | `WEAK_FINISHER` | $\bar{T}_{\text{second}} - \bar{T}_{\text{first}} \ge 100\text{ms}$ (홀수 $N$ 시 중앙 과목 제외) |
| 5 | `FLUCTUATING_PACE` | 위 1~4에 해당하지 않는 나머지 |

#### 5-4. Primary / Secondary 선택 규칙

3개 축에서 각각 1개씩 총 3개의 코드가 선정된 후, 응답은 **2개**만 반환한다.
- **`primary`**: 3개 축 중 가장 우선순위가 높은 축의 코드 (1순위)
- **`secondary`**: 1순위 축 다음으로 우선순위가 높은 축의 코드 (2순위)
- 축 우선순위 비교 규칙: 동일 우선순위 번호면 더 앞서는 축이 우선 (Physical > Entry & Start > Pace & Focus)