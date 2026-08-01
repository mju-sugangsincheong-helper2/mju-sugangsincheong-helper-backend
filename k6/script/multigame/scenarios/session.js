/**
 * Multigame - Session 시나리오
 *
 * 대기방 입장(heartbeat 폴링) + 진입 + 과목별 신청(반복 폴링) 시뮬레이션.
 * 가장 높은 부하가 발생하는 핵심 시나리오.
 *
 * 동작:
 * 1. 대기방 입장 (heartbeat 갱신 + 상태 확인)
 * 2. 상태가 PROGRESS이면 메인 방 진입(POST /enter) 후 여러 과목을 각각 신청
 * 3. 상태가 WAITING/READY이면 대기 후 재시도
 */
import { sleep } from 'k6';
import { guestLogin } from '../../common/login.js';
import { profiles, thresholds } from '../load-profiles.js';
import {
  enterWaitingRoom,
  enterGame,
  leaveGame,
  requestGame,
  getRandomSubjectIds,
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
    // 메인 방 진입 (신청 API는 진입 후에만 허용)
    const enterResult = enterGame(token);
    if (!enterResult) return;

    // 한 라운드에서 과목별로 각각 성공 가능 — 랜덤 3개 과목을 독립적으로 신청
    const subjectIds = getRandomSubjectIds(3);
    for (const subjectId of subjectIds) {
      // 클라이언트처럼 성공/마감/중복 응답이 나올 때까지 동일 요청을 반복 폴링
      for (let attempt = 0; attempt < 5; attempt++) {
        const gameResult = requestGame(token, subjectId);
        if (!gameResult) break;
        if (gameResult.status === 'SUCCESS'
          || gameResult.status === 'FAIL_SOLDOUT'
          || gameResult.status === 'FAIL_DUPLICATE') {
          break;
        }
        // PENDING/BLOCKED → 재시도
        sleep(0.5);
      }
      sleep(0.2);
    }

    // 3. 이탈 (대기열/참여자 마킹 정리)
    leaveGame(token);
  } else if (waitingResult.state === 'WAITING' || waitingResult.state === 'READY') {
    // 게임 시작 전: 3초 폴링 시뮬레이션
    sleep(3);
  }
  // ENDED, CANCELLED, CLOSED: 게임 종료/취소/미운영, 아무것도 안함
}
