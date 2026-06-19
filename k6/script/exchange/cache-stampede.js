import { sleep } from 'k6';
import http from 'k6/http';
import { SharedArray } from 'k6/data';
import { testLogin } from '../common/login.js';
import { exchangeThresholds } from '../common/thresholds.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
  stages: [
    { target: 500, duration: '20s' },
    { target: 1500, duration: '30s' },
    { target: 1500, duration: '180s' },
    { target: 0, duration: '20s' },
  ],
  thresholds: exchangeThresholds,
  noConnectionReuse: true,
};

const coursePairs = new SharedArray('course-pairs', function () {
  const pairs = [];
  for (let i = 0; i < 200; i++) {
    const give = String(20000 + Math.floor(Math.random() * 50000));
    let want = String(20000 + Math.floor(Math.random() * 50000));
    while (want === give) {
      want = String(20000 + Math.floor(Math.random() * 50000));
    }
    pairs.push({ giveCourseNo: give, wantCourseNo: want });
  }
  return pairs;
});

const testUsers = new SharedArray('test-users', function () {
  const users = [];
  for (let i = 0; i < 1500; i++) {
    users.push(`stampede_user_${i}`);
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

  // 90%: write-heavy eviction triggers
  if (__VU % 10 === 0) {
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

    if (Math.random() < 0.3 && intentRes.status === 201) {
      const intentId = intentRes.json('data.intentId');
      if (intentId) {
        http.del(
          `${BASE_URL}/api/v1/exchange/intents/${intentId}`,
          null,
          { tags: { name: 'DELETE_intent' }, ...params }
        );
      }
    }
  } else {
    // 90%: pure polling (cache consumer)
    http.get(
      `${BASE_URL}/api/v1/exchange/main`,
      { tags: { name: 'GET_main' }, ...params }
    );
  }

  sleep(5);
}
