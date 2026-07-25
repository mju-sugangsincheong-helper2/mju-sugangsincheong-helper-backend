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
    { duration: '30s', target: 50 },
    { duration: '1m', target: 50 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
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
    createReservation(token, multigameId);
    sleep(0.5);
    getMyReservations(token);
    sleep(0.5);
    hasReserved = true;
  }

  if (pollCount < MAX_POLLS) {
    const waitingRoomResult = enterWaitingRoom(token);

    if (waitingRoomResult && waitingRoomResult.state === 'PROGRESS') {
      const subjectId = getRandomSubjectId();
      const gameResult = requestGame(token, subjectId);

      if (gameResult && (gameResult.status === 'SUCCESS' || gameResult.status === 'FAIL_SOLDOUT')) {
        sleep(0.5);
        getMyResult(token, multigameId);
      }
    }

    pollCount++;
  }

  sleep(2);
}
