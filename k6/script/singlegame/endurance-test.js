import { sleep } from 'k6';
import http from 'k6/http';
import { SharedArray } from 'k6/data';
import { guestLogin } from '../common/login.js';
import { singlegameThresholds } from '../common/thresholds.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const DURATION_MINUTES = parseInt(__ENV.DURATION_MINUTES) || 60;

export const options = {
  stages: [
    { target: 300, duration: '30s' },
    { target: 300, duration: `${DURATION_MINUTES * 60}s` },
    { target: 0, duration: '30s' },
  ],
  thresholds: singlegameThresholds,
  noConnectionReuse: true,
};

const gamePayloads = new SharedArray('game-payloads', function () {
  const payloads = [];
  for (let i = 0; i < 50; i++) {
    const totalCourses = [1, 3, 6, 7, 8][Math.floor(Math.random() * 5)];
    const details = [];
    for (let j = 0; j < totalCourses; j++) {
      details.push({
        sequence: j + 1,
        tClickCourse: Math.floor(Math.random() * 400) + 100,
        tClickYes: Math.floor(Math.random() * 300) + 50,
        tClickOk: Math.floor(Math.random() * 300) + 50,
      });
    }
    payloads.push({
      totalCourses,
      isCompleted: true,
      tEnterMain: Math.floor(Math.random() * 500) + 100,
      details,
    });
  }
  return payloads;
});

let token;

export default function () {
  if (!token) {
    token = guestLogin();
  }
  if (!token) return;

  const params = {
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
  };

  // 1. Sleep for 10-20 seconds to simulate a single play session
  sleep(Math.random() * 10 + 10);

  // 2. Submit score
  const payload = gamePayloads[Math.floor(Math.random() * gamePayloads.length)];
  const postRes = http.post(
    `${BASE_URL}/api/v1/singlegame`,
    JSON.stringify(payload),
    { tags: { name: 'POST_singlegame' }, ...params }
  );

  // 3. Query rankings (GLOBAL or DEPARTMENT)
  const scope = Math.random() < 0.8 ? 'GLOBAL' : 'DEPARTMENT';
  http.get(
    `${BASE_URL}/api/v1/singlegame/rank?totalCourses=${payload.totalCourses}&scope=${scope}`,
    { tags: { name: 'GET_rank' }, ...params }
  );

  // 4. Query my records list
  http.get(
    `${BASE_URL}/api/v1/singlegame/my?page=0&size=10`,
    { tags: { name: 'GET_my_records' }, ...params }
  );

  // 5. Query detailed game analysis if the post request was successful and returned gameId
  if (postRes.status === 201) {
    try {
      const body = JSON.parse(postRes.body);
      const gameId = body.data && body.data.gameId;
      if (gameId) {
        http.get(
          `${BASE_URL}/api/v1/singlegame/${gameId}/analysis`,
          { tags: { name: 'GET_analysis' }, ...params }
        );
      }
    } catch (e) {
      // Ignore json parse error
    }
  }
}
