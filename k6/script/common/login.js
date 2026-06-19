import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export function guestLogin() {
  const res = http.post(`${BASE_URL}/api/v1/auth/guest`, null, {
    tags: { name: 'GuestLogin' },
  });
  check(res, { 'guest login 201': (r) => r.status === 201 });
  return extractToken(res);
}

export function testLogin(name) {
  const res = http.post(`${BASE_URL}/api/v1/auth/test-login?name=${name}`, null, {
    tags: { name: 'TestLogin' },
  });
  check(res, { 'test login 200': (r) => r.status === 200 });
  return extractToken(res);
}

function extractToken(res) {
  if (res.json('accessToken')) {
    return res.json('accessToken');
  }
  const authHeader = res.headers['Authorization'] || res.headers['authorization'];
  if (authHeader) {
    return authHeader.replace('Bearer ', '');
  }
  if (res.headers['X-Access-Token']) {
    return res.headers['X-Access-Token'];
  }
  return null;
}
