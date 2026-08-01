/**
 * Multigame - Full Flow 시나리오
 *
 * 대기방 → 진입 → 과목별 신청 → 이탈 → 결과 조회 전체 플로우.
 * 실제 유저 행동을 가장 가까이 시뮬레이션.
 *
 * (주의) 구 설계의 lifecycle API(수동 상태 전이)는 제거되어 더 이상 존재하지 않는다.
 * dev 환경에서도 스케줄러가 10분 주기로 동작하므로, 시나리오는 현재 게임 상태에 따라
 * PROGRESS면 신청 단계를 수행하고, 그 외 상태면 폴링만 하며 결과/랭킹 조회는 항상 수행한다.
 */
import { sleep } from 'k6';
import { guestLogin } from '../../common/login.js';
import { profiles, thresholds } from '../load-profiles.js';
import {
  enterWaitingRoom,
  enterGame,
  leaveGame,
  requestGame,
  getGameResult,
  getMyHistory,
  getRankings,
  computeActiveMultigameId,
  getRandomSubjectIds,
} from '../helpers.js';

const tier = __ENV.LOAD_TIER || 'small';
export const options = {
  ...profiles[tier],
  thresholds: thresholds['full-flow'],
};

export default function () {
  const token = guestLogin();
  if (!token) return;

  // 1. 대기방 입장 (heartbeat 갱신 + 상태 확인)
  const waitingResult = enterWaitingRoom(token);
  if (!waitingResult) return;

  if (waitingResult.state === 'PROGRESS') {
    // 2. 메인 방 진입
    const enterResult = enterGame(token);
    if (!enterResult) return;

    // 3. 과목별 신청 (최대 6개 중 랜덤 3개, 과목별 독립 성공 — 폴링 재시도 포함)
    const subjectIds = getRandomSubjectIds(3);
    for (const subjectId of subjectIds) {
      for (let attempt = 0; attempt < 5; attempt++) {
        const gameResult = requestGame(token, subjectId);
        if (!gameResult) break;
        if (gameResult.status === 'SUCCESS'
          || gameResult.status === 'FAIL_SOLDOUT'
          || gameResult.status === 'FAIL_DUPLICATE') {
          break;
        }
        sleep(0.5); // PENDING/BLOCKED → 재시도
      }
      sleep(0.2);
    }

    // 4. 이탈
    leaveGame(token);
  } else if (waitingResult.state === 'WAITING' || waitingResult.state === 'READY') {
    // 대기 중: 폴링 시뮬레이션
    sleep(3);
  }

  // 5. 마지막 종료 라운드 상세 조회 (게임 진행 중이면 404 허용)
  sleep(1);
  const multigameId = computeActiveMultigameId();
  getGameResult(token, multigameId);
  sleep(0.3);

  // 6. 내 참여 기록 + 학과 랭킹
  getMyHistory(token, 0, 5);
  sleep(0.3);
  getRankings(token);
}
