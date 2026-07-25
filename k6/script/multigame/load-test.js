import { sleep, check } from 'k6';
import http from 'k6/http';
import { guestLogin } from '../common/login.js';
import { multigameThresholds } from '../common/thresholds.js';
import {
  createReservation,
  getMyReservations,
  enterWaitingRoom,
  requestGame,
  getMyResult,
  computeNextMultigameId,
  getRandomSubjectId,
} from './helpers.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const VU_MAX = parseInt(__ENV.VU_MAX) || 100;

export const options = {
  stages: [
    { target: VU_MAX, duration: '30s' },
    { target: VU_MAX, duration: '2m' },
    { target: 0, duration: '30s' },
  ],
  thresholds: multigameThresholds,
};

let token = null;
let hasReserved = false;
let waitingRoomPollCount = 0;
const MAX_WAITING_ROOM_POLLS = 5;

export default function () {
  if (!token) {
    token = guestLogin();
  }
  if (!token) {
    sleep(1);
    return;
  }

  const multigameId = computeNextMultigameId();

  if (!hasReserved) {
    createReservation(token, multigameId);
    sleep(0.5);

    getMyReservations(token);
    sleep(0.5);

    hasReserved = true;
  }

  if (waitingRoomPollCount < MAX_WAITING_ROOM_POLLS) {
    const waitingRoomResult = enterWaitingRoom(token);

    if (waitingRoomResult && waitingRoomResult.state === 'PROGRESS') {
      const subjectId = getRandomSubjectId();
      const gameResult = requestGame(token, subjectId);

      if (gameResult && (gameResult.status === 'SUCCESS' || gameResult.status === 'FAIL_SOLDOUT' || gameResult.status === 'FAIL_DUPLICATE')) {
        sleep(1);
        getMyResult(token, multigameId);
      }
    }

    waitingRoomPollCount++;
  }

  sleep(3);
}
