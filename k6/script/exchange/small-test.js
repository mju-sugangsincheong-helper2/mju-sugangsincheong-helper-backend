import http from 'k6/http';
import { check, sleep } from 'k6';
import { guestLogin } from '../common/login.js';

export const options = {
  stages: [
    { duration: '30s', target: 30 },
    { duration: '1m', target: 30 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
  },
};

export default function () {
  const token = guestLogin();
  if (!token) return;

  const params = {
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
  };

  const mainRes = http.get(
    'http://localhost:8080/api/v1/exchange/main',
    { ...params, tags: { name: 'GET_main' } }
  );

  check(mainRes, {
    'main page retrieved': (r) => r.status === 200,
  });

  sleep(1);

  const intentPayload = JSON.stringify({
    giveCourseNo: '10023',
    wantCourseNo: '40101',
  });

  const intentRes = http.post(
    'http://localhost:8080/api/v1/exchange/intents',
    intentPayload,
    { ...params, tags: { name: 'POST_intents' } }
  );

  check(intentRes, {
    'intent created': (r) => r.status === 200 || r.status === 201,
  });

  sleep(1);

  const mainAfterRes = http.get(
    'http://localhost:8080/api/v1/exchange/main',
    { ...params, tags: { name: 'GET_main_after' } }
  );

  check(mainAfterRes, {
    'main page after intent': (r) => r.status === 200,
  });

  sleep(1);
}
