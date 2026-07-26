# 싱글 게임 단위 테스트 명세서

본 문서는 싱글 게임 서비스 및 피드백 엔진(`SingleGameFeedbackEngine`)의 단위 테스트 사양을 명확히 요약합니다.

---

## 1. 도메인 유효성 검증
- **과목 수 (`totalCourses`)**: `1`, `3`, `6`, `7`, `8`만 허용 (`0`, `2`, `4`, `5`, `9` 등 거부)
- **완료 여부 (`isCompleted`) 정합성**: `isCompleted = true` 시 `details.size() == totalCourses`, `false` 시 `details.size() < totalCourses`
- **반응 속도 범위**: `tEnterMain`, `tClickCourse`, `tClickYes`, `tClickOk` 모두 `1ms` ~ `60,000ms` 범위 내 준수

---

## 2. 지표 연산 정합성
- **과목 처리 시간 ($T_i$)**: $T_i = \text{tClickCourse}_i + \text{tClickYes}_i + \text{tClickOk}_i$
- **초반 스퍼트 ($N \ge 3$)**: $T_1 - \text{avg}(T_2 \dots T_N)$ ($N < 3$ 시 `null`)
- **페이스 편차 ($N \ge 3$)**: $T_i$ 모표준편차 $\sigma$ ($N < 3$ 시 `null`)

---

## 3. 피드백 엔진 규칙 (`SingleGameFeedbackEngine`)

| 축 | 우선순위 | 코드 | 판정 조건 |
|---|---|---|---|
| **Physical** | 1 | `GOD_TIER_PHYSICAL` | $\text{aimP} \le 30 \land \text{burstP} \le 30$ |
| | 2 | `PHYSICAL_UPGRADE_NEEDED` | $\text{aimP} \ge 70 \land \text{burstP} \ge 70$ |
| | 3 | `FAST_BUT_INACCURATE` | $\text{aimP} \ge 70 \land \text{burstP} \le 30$ |
| **Entry & Start** | 4 | `PERFECT_ENTRY_START` | $\text{eP} \le 30 \land \text{startP} \le 30$ |
| | 5 | `ENTRY_MASTER_START_NOVICE` | $\text{eP} \le 30 \land \text{startP} \ge 70$ |
| | 6 | `ENTRY_LATE_START_MASTER` | $\text{eP} \ge 70 \land \text{startP} \le 30$ |
| | 7 | `NEED_FASTER_ENTRY` | $\text{eP} \ge 70 \land \text{startP} > 30$ |
| **Pace & Focus** | 8 | `MACHINE_LIKE_PACE` | $N \ge 3 \land \text{paceP} \le 30$ |
| ($N \ge 3$) | 9 | `EASY_PANIC` | $N \ge 3 \land \exists T_i > \bar{T} + 1.5\sigma$ |
| | 10 | `STRONG_FINISHER` | $N \ge 3 \land \bar{T}_{\text{first}} - \bar{T}_{\text{second}} \ge 100$ (홀수 $N$ 시 중앙 과목 제외) |
| | 11 | `WEAK_FINISHER` | $N \ge 3 \land \bar{T}_{\text{second}} - \bar{T}_{\text{first}} \ge 100$ (홀수 $N$ 시 중앙 과목 제외) |
| **Physical Fallback**| 12 | `SLOW_AIM` | $\text{aimP} > \text{burstP}$ |
| | 13 | `SLOW_BURST` | $\text{burstP} > \text{aimP}$ |
| **Fallback** | 14 | `FLUCTUATING_PACE` | $N \ge 3 \land \sigma > 0$ |
| | 15 | `START_HESITATION` | $\text{startP} \ge 50$ |