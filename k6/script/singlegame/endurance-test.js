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

export default function () {
  const token = guestLogin();
  if (!token) return;

  const params = {
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
  };

  const payload = gamePayloads[Math.floor(Math.random() * gamePayloads.length)];
  http.post(
    `${BASE_URL}/api/v1/singlegame`,
    JSON.stringify(payload),
    { tags: { name: 'POST_singlegame' }, ...params }
  );

  http.get(
    `${BASE_URL}/api/v1/singlegame/rank?totalCourses=${payload.totalCourses}&scope=GLOBAL`,
    { tags: { name: 'GET_rank' }, ...params }
  );

  sleep(Math.random() * 5 + 10);
}
