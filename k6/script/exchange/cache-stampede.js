import { sleep } from 'k6';
import http from 'k6/http';
import { SharedArray } from 'k6/data';
import { testLogin } from '../common/login.js';
import { exchangeThresholds } from '../common/thresholds.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const VU_MAX = parseInt(__ENV.VU_MAX) || 1500;

export const options = {
  stages: [
    { target: Math.floor(VU_MAX * 0.3), duration: '20s' },
    { target: VU_MAX, duration: '30s' },
    { target: VU_MAX, duration: '180s' },
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
  for (let i = 0; i < 2000; i++) {
    users.push(`stampede_user_${i}`);
  }
  return users;
});

let token;

export default function () {
  if (!token) {
    const userName = testUsers[__VU - 1] || `stampede_user_${__VU}`;
    token = testLogin(userName);
  }
  if (!token) return;

  const params = {
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
  };

  // 10% of VUs act as Writers (evictors)
  if (__VU % 10 === 0) {
    const pair = coursePairs[Math.floor(Math.random() * coursePairs.length)];
    
    // 1. Post intent (triggers search, match, and potential caching eviction)
    const intentRes = http.post(
      `${BASE_URL}/api/v1/exchange/intents`,
      JSON.stringify(pair),
      { tags: { name: 'POST_intents' }, ...params }
    );

    // 2. Fetch main screen status
    let roomId = null;
    const mainRes = http.get(
      `${BASE_URL}/api/v1/exchange/main`,
      { tags: { name: 'GET_main' }, ...params }
    );

    if (mainRes.status === 200) {
      try {
        const body = JSON.parse(mainRes.body);
        if (body.data && body.data.rooms && body.data.rooms.length > 0) {
          roomId = body.data.rooms[0].roomId;
        }
      } catch (e) {
        // Ignore json parse error
      }
    }

    // 3. Send message to room if matched, which evicts message/room cache for other members
    if (roomId && Math.random() < 0.5) {
      http.post(
        `${BASE_URL}/api/v1/exchange/rooms/${roomId}/messages`,
        JSON.stringify({ content: `Eviction triggering test message ${Math.random()}` }),
        { tags: { name: 'POST_message' }, ...params }
      );
    }

    // 4. Sometimes delete/retract the intent to cause further evictions
    if (Math.random() < 0.3 && intentRes.status === 201) {
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

  } else {
    // 90% of VUs act as Pollers (cache consumers)
    http.get(
      `${BASE_URL}/api/v1/exchange/main`,
      { tags: { name: 'GET_main' }, ...params }
    );
  }

  sleep(5);
}
