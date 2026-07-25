import { sleep, check } from 'k6';
import http from 'k6/http';
import { guestLogin } from '../common/login.js';
import {
  enterWaitingRoom,
  requestGame,
  getRandomSubjectId,
} from './helpers.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const VU_MAX = parseInt(__ENV.VU_MAX) || 1000;

export const options = {
  stages: [
    { target: VU_MAX, duration: '5s' },
    { target: VU_MAX, duration: '25s' },
    { target: 0, duration: '5s' },
  ],
  thresholds: {
    http_req_failed: ['rate<0.05'],
    'http_req_duration{name:POST_waiting_room}': ['p(95)<100'],
    'http_req_duration{name:POST_game_request}': ['p(95)<50', 'p(99)<100'],
  },
  noConnectionReuse: true,
};

let token = null;
let pollCount = 0;
const MAX_POLLS = 15;

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

    if (waitingRoomResult && waitingRoomResult.state === 'PROGRESS') {
      const subjectId = getRandomSubjectId();
      const gameResult = requestGame(token, subjectId);

      if (gameResult) {
        check(gameResult.status, {
          'game request has valid status': (status) =>
            ['PENDING', 'SUCCESS', 'FAIL_SOLDOUT', 'FAIL_DUPLICATE', 'BLOCKED', 'WAITING'].includes(status),
        });
      }
    }

    pollCount++;
  }

  sleep(1);
}
