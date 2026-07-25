import { sleep } from 'k6';
import { guestLogin } from '../common/login.js';
import {
  createReservation,
  getMyReservations,
  enterWaitingRoom,
  requestGame,
  getMyResult,
  computeNextMultigameId,
  getRandomSubjectId,
} from './helpers.js';

export const options = {
  stages: [
    { duration: '1m', target: 200 },
    { duration: '2m', target: 200 },
    { duration: '1m', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<800'],
    http_req_failed: ['rate<0.05'],
  },
};

let hasReserved = false;
let pollCount = 0;
const MAX_POLLS = 10;

export default function () {
  const token = guestLogin();
  if (!token) return;

  const multigameId = computeNextMultigameId();

  if (!hasReserved) {
    // 예약 생성
    createReservation(token, multigameId);
    sleep(0.5);

    // 내 예약 조회
    getMyReservations(token);
    sleep(0.5);

    hasReserved = true;
  }

  if (pollCount < MAX_POLLS) {
    // 대기방 입장 (heartbeat)
    const waitingRoomResult = enterWaitingRoom(token);

    if (waitingRoomResult && waitingRoomResult.state === 'PROGRESS') {
      // 게임 요청
      const subjectId = getRandomSubjectId();
      const gameResult = requestGame(token, subjectId);

      if (gameResult && (gameResult.status === 'SUCCESS' || gameResult.status === 'FAIL_SOLDOUT')) {
        sleep(0.5);
        // 결과 조회
        getMyResult(token, multigameId);
      }
    }

    pollCount++;
  }

  sleep(2);
}
