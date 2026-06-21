import { sleep } from 'k6';
import http from 'k6/http';
import { SharedArray } from 'k6/data';
import { guestLogin } from '../common/login.js';
import { singlegameThresholds } from '../common/thresholds.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const VU_MAX = parseInt(__ENV.VU_MAX) || 500;

export const options = {
  stages: [
    { target: Math.floor(VU_MAX * 0.2), duration: '10s' },
    { target: Math.floor(VU_MAX * 0.6), duration: '20s' },
    { target: VU_MAX, duration: '30s' },
    { target: VU_MAX, duration: '60s' },
    { target: 0, duration: '10s' },
  ],
  thresholds: singlegameThresholds,
  noConnectionReuse: true,
};

const gamePayloads = new SharedArray('game-payloads', function () {
  const payloads = [];
  for (let i = 0; i < 20; i++) {
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

const coursesList = [1, 3, 6, 7, 8];
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

  // 10% of users submit new game records, which can cause cache eviction or update overhead
  if (__VU % 10 === 0) {
    const payload = gamePayloads[Math.floor(Math.random() * gamePayloads.length)];
    http.post(
      `${BASE_URL}/api/v1/singlegame`,
      JSON.stringify(payload),
      { tags: { name: 'POST_singlegame' }, ...params }
    );
  } else {
    // 90% of users query rankings to simulate a high-frequency read and cache stampede scenario
    const totalCourses = coursesList[Math.floor(Math.random() * coursesList.length)];
    const scope = Math.random() < 0.7 ? 'GLOBAL' : 'DEPARTMENT';

    http.get(
      `${BASE_URL}/api/v1/singlegame/rank?totalCourses=${totalCourses}&scope=${scope}`,
      { tags: { name: 'GET_rank' }, ...params }
    );

    // Occasionally query their own records (20% chance)
    if (Math.random() < 0.2) {
      http.get(
        `${BASE_URL}/api/v1/singlegame/my?page=0&size=10`,
        { tags: { name: 'GET_my_records' }, ...params }
      );
    }
  }

  // Poll ranking/my records frequently with a small sleep
  sleep(Math.random() * 2 + 1);
}
