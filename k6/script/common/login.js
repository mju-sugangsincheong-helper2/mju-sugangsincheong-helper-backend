import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export function guestLogin() {
  const res = http.post(`${BASE_URL}/api/v1/auth/guest`, null, {
    tags: { name: 'GuestLogin' },
  });
  check(res, { 'guest login 201': (r) => r.status === 201 || r.status === 200 });
  return extractToken(res);
}

export function adminLogin() {
  return testLogin('TEST_ADMIN');
}

export function testLogin(name) {
  const res = http.post(`${BASE_URL}/api/v1/auth/test-login?name=${name}`, null, {
    tags: { name: 'TestLogin' },
  });
  check(res, { 'test login 200': (r) => r.status === 200 });
  return extractToken(res);
}

export function createTestMember() {
  const payload = JSON.stringify({ role: 'MEMBER' });
  const res = http.post(`${BASE_URL}/api/v1/auth/test-accounts`, payload, {
    tags: { name: 'CreateTestMember' },
    headers: { 'Content-Type': 'application/json' },
  });
  check(res, { 'test member created': (r) => r.status === 200 });
  return extractToken(res);
}

function extractToken(res) {
  try {
    const json = res.json();
    if (json) {
      if (json.data && json.data.accessToken) {
        return json.data.accessToken;
      }
      if (json.accessToken) {
        return json.accessToken;
      }
    }
  } catch (e) {
    // Ignore JSON parsing errors and fall back to headers
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
