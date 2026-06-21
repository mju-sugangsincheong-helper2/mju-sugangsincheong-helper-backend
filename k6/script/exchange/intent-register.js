import { sleep } from 'k6';
import http from 'k6/http';
import { SharedArray } from 'k6/data';
import { testLogin } from '../common/login.js';
import { exchangeThresholds } from '../common/thresholds.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const VU_MAX = parseInt(__ENV.VU_MAX) || 300;

export const options = {
  stages: [
    { target: Math.floor(VU_MAX * 0.2), duration: '10s' },
    { target: Math.floor(VU_MAX * 0.6), duration: '20s' },
    { target: VU_MAX, duration: '30s' },
    { target: VU_MAX, duration: '120s' },
    { target: 0, duration: '20s' },
  ],
  thresholds: exchangeThresholds,
  noConnectionReuse: true,
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
  for (let i = 0; i < 1000; i++) {
    users.push(`intent_user_${i}`);
  }
  return users;
});

let token;

export default function () {
  if (!token) {
    const userName = testUsers[__VU - 1] || `intent_user_${__VU}`;
    token = testLogin(userName);
  }
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

  // Check main screen to see matches/rooms
  http.get(
    `${BASE_URL}/api/v1/exchange/main`,
    { tags: { name: 'GET_main' }, ...params }
  );

  // 20% chance to delete/retract the intent to simulate eviction/cancellation
  if (Math.random() < 0.2 && intentRes.status === 201) {
    try {
      const body = JSON.parse(intentRes.body);
      const intentId = body.data && body.data.intentId;
      if (intentId) {
        http.del(
          `${BASE_URL}/api/v1/exchange/intents/${intentId}`,
          null,
          { tags: { name: 'DELETE_intent' }, ...params }
        );
      }
    } catch (e) {
      // Ignore json parse error
    }
  }

  // Sleep shortly to simulate continuous active registration & graph exploration
  sleep(Math.random() * 2 + 1);
}
