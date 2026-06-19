import { sleep } from 'k6';
import http from 'k6/http';
import { SharedArray } from 'k6/data';
import { testLogin } from '../common/login.js';
import { exchangeThresholds } from '../common/thresholds.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const VU_MAX = parseInt(__ENV.VU_MAX) || 2000;

export const options = {
  stages: [
    { target: VU_MAX, duration: '30s' },
    { target: VU_MAX, duration: '180s' },
    { target: 0, duration: '30s' },
  ],
  thresholds: exchangeThresholds,
  noConnectionReuse: true,
};

const testUsers = new SharedArray('test-users', function () {
  const users = [];
  for (let i = 0; i < 2000; i++) {
    users.push(`poll_user_${i}`);
  }
  return users;
});

export default function () {
  const userName = testUsers[__VU - 1];
  const token = testLogin(userName);
  if (!token) return;

  const params = {
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
  };

  http.get(
    `${BASE_URL}/api/v1/exchange/main`,
    { tags: { name: 'GET_main' }, ...params }
  );

  if (Math.random() < 0.3) {
    http.get(
      `${BASE_URL}/api/v1/exchange/intents/recent?lastIntentId=0&limit=10`,
      { tags: { name: 'GET_recent_intents' }, ...params }
    );
  }

  sleep(5);
}
