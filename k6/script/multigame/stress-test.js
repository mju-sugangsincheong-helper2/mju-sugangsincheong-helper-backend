import { sleep, check } from 'k6';
import http from 'k6/http';
import { guestLogin } from '../common/login.js';
import { multigameThresholds } from '../common/thresholds.js';
import {
  enterWaitingRoom,
  requestGame,
  getRandomSubjectId,
} from './helpers.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const VU_MAX = parseInt(__ENV.VU_MAX) || 500;

export const options = {
  stages: [
    { target: VU_MAX, duration: '10s' },
    { target: VU_MAX, duration: '30s' },
    { target: 0, duration: '10s' },
  ],
  thresholds: {
    http_req_failed: ['rate<0.05'],
    'http_req_duration{name:POST_waiting_room}': ['p(95)<200'],
    'http_req_duration{name:POST_game_request}': ['p(95)<100'],
  },
};

let token = null;
let pollCount = 0;
const MAX_POLLS = 10;

export default function () {
  if (!token) {
    token = guestLogin();
  }
  if (!token) {
    sleep(1);
    return;
  }

  if (pollCount < MAX_POLLS) {
    const waitingRoomResult = enterWaitingRoom(token);

    if (waitingRoomResult) {
      const subjectId = getRandomSubjectId();
      requestGame(token, subjectId);
    }

    pollCount++;
  }

  sleep(2);
}
