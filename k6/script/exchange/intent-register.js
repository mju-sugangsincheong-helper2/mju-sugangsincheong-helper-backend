import { sleep } from 'k6';
import http from 'k6/http';
import { SharedArray } from 'k6/data';
import { testLogin } from '../common/login.js';
import { exchangeThresholds } from '../common/thresholds.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
  stages: [
    { target: 50, duration: '10s' },
    { target: 200, duration: '20s' },
    { target: 300, duration: '30s' },
    { target: 300, duration: '120s' },
    { target: 0, duration: '20s' },
  ],
  thresholds: exchangeThresholds,
};

const coursePairs = new SharedArray('course-pairs', function () {
  const pairs = [];
  for (let i = 0; i < 1000; i++) {
    const give = String(10000 + Math.floor(Math.random() * 90000));
    let want = String(10000 + Math.floor(Math.random() * 90000));
    while (want === give) {
      want = String(10000 + Math.floor(Math.random() * 90000));
    }
    pairs.push({ giveCourseNo: give, wantCourseNo: want });
  }
  return pairs;
});

const testUsers = new SharedArray('test-users', function () {
  const users = [];
  for (let i = 0; i < 500; i++) {
    users.push(`intent_user_${i}`);
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

  const pair = coursePairs[Math.floor(Math.random() * coursePairs.length)];
  const intentRes = http.post(
    `${BASE_URL}/api/v1/exchange/intents`,
    JSON.stringify(pair),
    { tags: { name: 'POST_intents' }, ...params }
  );

  http.get(
    `${BASE_URL}/api/v1/exchange/main`,
    { tags: { name: 'GET_main' }, ...params }
  );

  if (Math.random() < 0.2 && intentRes.status === 201) {
    const intentId = intentRes.json('data.intentId');
    if (intentId) {
      http.del(
        `${BASE_URL}/api/v1/exchange/intents/${intentId}`,
        null,
        { tags: { name: 'DELETE_intent' }, ...params }
      );
    }
  }

  sleep(Math.random() * 3 + 2);
}
