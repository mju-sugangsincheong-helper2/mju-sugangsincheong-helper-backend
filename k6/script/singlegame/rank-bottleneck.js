import { sleep } from 'k6';
import http from 'k6/http';
import { guestLogin } from '../common/login.js';
import { singlegameThresholds } from '../common/thresholds.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
  stages: [
    { target: 100, duration: '10s' },
    { target: 300, duration: '20s' },
    { target: 500, duration: '30s' },
    { target: 500, duration: '60s' },
    { target: 0, duration: '10s' },
  ],
  thresholds: singlegameThresholds,
};

const coursesList = [1, 3, 6, 7, 8];

export default function () {
  const token = guestLogin();
  if (!token) return;

  const params = {
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
  };

  const totalCourses = coursesList[Math.floor(Math.random() * coursesList.length)];
  const scope = Math.random() < 0.7 ? 'GLOBAL' : 'DEPARTMENT';

  http.get(
    `${BASE_URL}/api/v1/singlegame/rank?totalCourses=${totalCourses}&scope=${scope}`,
    { tags: { name: 'GET_rank' }, ...params }
  );

  if (Math.random() < 0.2) {
    http.get(
      `${BASE_URL}/api/v1/singlegame/my?page=0&size=10`,
      { tags: { name: 'GET_my_records' }, ...params }
    );
  }

  sleep(Math.random() * 3 + 2);
}
