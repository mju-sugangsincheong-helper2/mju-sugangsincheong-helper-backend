import http from 'k6/http';
import { check, sleep } from 'k6';
import { guestLogin } from '../common/login.js';

export const options = {
  stages: [
    { duration: '1m', target: 200 },
    { duration: '2m', target: 200 },
    { duration: '1m', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<800'],
    http_req_failed: ['rate<0.05'],
  },
};

export default function () {
  const token = guestLogin();
  if (!token) return;

  const params = {
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
  };

  const payload = JSON.stringify({
    totalCourses: 6,
    isCompleted: true,
    tTotal: 5000,
    tEnterMain: 1000,
    details: [
      { sequence: 1, tClickCourse: 300, tClickYes: 200, tClickOk: 200 },
      { sequence: 2, tClickCourse: 300, tClickYes: 200, tClickOk: 200 },
      { sequence: 3, tClickCourse: 300, tClickYes: 200, tClickOk: 200 },
      { sequence: 4, tClickCourse: 300, tClickYes: 200, tClickOk: 200 },
      { sequence: 5, tClickCourse: 300, tClickYes: 200, tClickOk: 200 },
      { sequence: 6, tClickCourse: 300, tClickYes: 200, tClickOk: 200 },
    ],
  });

  const saveRes = http.post(
    'http://localhost:8080/api/v1/singlegame',
    payload,
    { ...params, tags: { name: 'POST_singlegame' } }
  );

  check(saveRes, {
    'game saved': (r) => r.status === 200 || r.status === 201,
  });

  sleep(1);

  const rankRes = http.get(
    'http://localhost:8080/api/v1/singlegame/rank?totalCourses=6&scope=GLOBAL',
    { ...params, tags: { name: 'GET_rank' } }
  );

  check(rankRes, {
    'rank retrieved': (r) => r.status === 200,
  });

  sleep(1);

  const myRes = http.get(
    'http://localhost:8080/api/v1/singlegame/my?page=0&size=10',
    { ...params, tags: { name: 'GET_my' } }
  );

  check(myRes, {
    'my records retrieved': (r) => r.status === 200,
  });

  sleep(1);
}
