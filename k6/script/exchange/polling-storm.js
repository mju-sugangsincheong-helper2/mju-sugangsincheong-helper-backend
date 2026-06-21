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

let token;
let cachedRoomId = null;
let step = 0;

export default function () {
  if (!token) {
    const userName = testUsers[__VU - 1] || `poll_user_${__VU}`;
    token = testLogin(userName);
  }
  if (!token) return;

  const params = {
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
  };

  // Alternately call /main and /rooms/{id}/messages every 5s
  if (step % 2 === 0 || !cachedRoomId) {
    const mainRes = http.get(
      `${BASE_URL}/api/v1/exchange/main`,
      { tags: { name: 'GET_main' }, ...params }
    );

    if (mainRes.status === 200) {
      try {
        const body = JSON.parse(mainRes.body);
        if (body.data && body.data.rooms && body.data.rooms.length > 0) {
          cachedRoomId = body.data.rooms[0].roomId;
        }
      } catch (e) {
        // Ignore json parse error
      }
    }
  } else {
    http.get(
      `${BASE_URL}/api/v1/exchange/rooms/${cachedRoomId}/messages?lastMessageId=999999999&size=20`,
      { tags: { name: 'GET_messages' }, ...params }
    );
  }

  step++;
  sleep(5);
}
