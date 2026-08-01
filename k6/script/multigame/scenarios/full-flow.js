/**
 * Multigame - Full Flow 시나리오
 *
 * 예약 → 대기방 → 게임 신청 → 결과 조회 전체 플로우.
 * 실제 유저 행동을 가장 가까이 시뮬레이션.
 *
 * dev 환경에서는 lifecycle API를 사용하여 게임 상태를 수동으로 전이합니다.
 */
import { sleep } from 'k6';
import http from 'k6/http';
import { guestLogin, adminLogin } from '../../common/login.js';
import { profiles, thresholds } from '../load-profiles.js';
import {
  createReservation,
  getMyReservations,
  enterWaitingRoom,
  requestGame,
  getGameResult,
  getMyHistory,
  getDashboard,
  computeActiveMultigameId,
  computeReservableMultigameId,
  getRandomSubjectId,
  transitionGameState,
  getGameState,
} from '../helpers.js';

const tier = __ENV.LOAD_TIER || 'small';
export const options = {
  ...profiles[tier],
  thresholds: thresholds['full-flow'],
};

/**
 * setup: 테스트 시작 전 게임 상태를 PROGRESS로 전이
 * dev 환경에서는 스케줄러가 비활성화되어 있으므로 lifecycle API를 사용합니다.
 */
export function setup() {
  const adminToken = adminLogin();
  if (!adminToken) {
    console.warn('ADMIN 로그인 실패. 상태 전이를 건너뜁니다.');
    return { adminToken: null };
  }

  const multigameId = computeReservableMultigameId();
  console.log(`Setup: 게임 ${multigameId} 상태를 PROGRESS로 전이합니다.`);

  // 예약 생성 (참여자 확보를 위해)
  const guestToken = guestLogin();
  if (guestToken) {
    createReservation(guestToken, multigameId);
  }

  // 상태 전이: WAITING → READY → PROGRESS
  const currentState = getGameState(adminToken, multigameId);
  console.log(`Setup: 현재 상태 = ${currentState}`);

  if (currentState === 'WAITING') {
    transitionGameState(adminToken, multigameId, 'READY');
    sleep(0.5);
    transitionGameState(adminToken, multigameId, 'PROGRESS');
    sleep(0.5);
  } else if (currentState === 'READY') {
    transitionGameState(adminToken, multigameId, 'PROGRESS');
    sleep(0.5);
  }

  const finalState = getGameState(adminToken, multigameId);
  console.log(`Setup: 최종 상태 = ${finalState}`);

  return { adminToken, multigameId };
}

export default function (data) {
  const token = guestLogin();
  if (!token) return;

  // 예약은 최소 10분 이후의 게임에 대해 생성
  const reservableMultigameId = computeReservableMultigameId();
  // 대기방/게임 신청은 setup에서 PROGRESS로 전이한 게임 대상
  const activeMultigameId = data.multigameId || computeActiveMultigameId();

  // 1. 예약 생성
  createReservation(token, reservableMultigameId);
  sleep(0.5);

  // 2. 내 예약 확인
  getMyReservations(token);
  sleep(0.5);

  // 3. 대기방 입장 (PROGRESS로 전이된 게임)
  const waitingRoomResult = enterWaitingRoom(token);
  if (!waitingRoomResult) return;

  // 4. 게임 상태에 따른 분기
  if (waitingRoomResult.state === 'PROGRESS') {
    const subjectId = getRandomSubjectId();
    const gameResult = requestGame(token, subjectId);

    if (gameResult && (gameResult.status === 'SUCCESS' || gameResult.status === 'FAIL_SOLDOUT')) {
      sleep(0.5);
      // 5. 결과 상세 조회 (분석서 + 내 결과 + 내 신청 로그)
      getGameResult(token, activeMultigameId);
    }
  } else if (waitingRoomResult.state === 'WAITING' || waitingRoomResult.state === 'READY') {
    // 대기 중: 폴링 시뮬레이션
    sleep(3);
  }

  // 6. 부가 정보 조회
  sleep(1);
  getMyHistory(token, 0, 5);
  sleep(0.5);
  getDashboard(token);
}
