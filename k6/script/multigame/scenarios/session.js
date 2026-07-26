/**
 * Multigame - Session 시나리오
 *
 * 대기방 입장(heartbeat 폴링) + 게임 신청(반복 요청) 시뮬레이션.
 * 가장 높은 부하가 발생하는 핵심 시나리오.
 *
 * 동작:
 * 1. 대기방 입장 (heartbeat 갱신 + 상태 확인)
 * 2. 상태가 PROGRESS이면 게임 신청 반복
 * 3. 상태가 WAITING/READY이면 대기 후 재시도
 */
import { sleep } from 'k6';
import { guestLogin } from '../../common/login.js';
import { profiles, thresholds } from '../load-profiles.js';
import {
  enterWaitingRoom,
  requestGame,
  getRandomSubjectId,
} from '../helpers.js';

const tier = __ENV.LOAD_TIER || 'small';
export const options = {
  ...profiles[tier],
  thresholds: thresholds.session,
};

export default function () {
  const token = guestLogin();
  if (!token) return;

  // 1. 대기방 입장 (heartbeat 갱신)
  const waitingResult = enterWaitingRoom(token);
  if (!waitingResult) return;

  // 2. 상태에 따른 분기
  if (waitingResult.state === 'PROGRESS') {
    // 게임 진행 중: 신청 반복 (클라이언트가 같은 요청을 계속 보내는 방식)
    const subjectId = getRandomSubjectId();
    const gameResult = requestGame(token, subjectId);

    // 신청 결과 확인 후 짧은 대기
    if (gameResult) {
      sleep(0.5);
      // 이미 SUCCESS/FAIL_SOLDOUT이면 큐에서 제거됨
      // PENDING이면 계속 폴링
      if (gameResult.status === 'PENDING') {
        sleep(1);
        requestGame(token, subjectId);
      }
    }
  } else if (waitingResult.state === 'WAITING' || waitingResult.state === 'READY') {
    // 게임 시작 전: 3초 폴링 시뮬레이션
    sleep(3);
  }
  // ENDED, FINALIZE: 게임 종료, 아무것도 안함
}
