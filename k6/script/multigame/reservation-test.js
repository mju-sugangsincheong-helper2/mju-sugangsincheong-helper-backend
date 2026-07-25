import { sleep, check } from 'k6';
import http from 'k6/http';
import { guestLogin } from '../common/login.js';
import { multigameThresholds } from '../common/thresholds.js';
import {
  createReservation,
  getMyReservations,
  getAllReservations,
  computeNextMultigameId,
} from './helpers.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const VU_MAX = parseInt(__ENV.VU_MAX) || 200;

export const options = {
  stages: [
    { target: VU_MAX, duration: '20s' },
    { target: VU_MAX, duration: '1m' },
    { target: 0, duration: '20s' },
  ],
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{name:POST_reservations}': ['p(95)<300'],
    'http_req_duration{name:GET_reservations_my}': ['p(95)<150'],
    'http_req_duration{name:GET_reservations}': ['p(95)<150'],
  },
};

let token = null;
let hasReserved = false;

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
    sleep(0.3);

    getMyReservations(token);
    sleep(0.3);

    getAllReservations(token);
    sleep(0.3);

    getAllReservations(token, multigameId);

    hasReserved = true;
  }

  sleep(2);
}
