import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// ============================================================
// Intent API
// ============================================================

export function createIntent(token, giveCourseNo, wantCourseNo) {
  const res = http.post(
    `${BASE_URL}/api/v1/exchange/intents`,
    JSON.stringify({ giveCourseNo, wantCourseNo }),
    {
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      tags: { name: 'POST_intents' },
    }
  );
  check(res, {
    'create intent status is 201/200': (r) => r.status === 201 || r.status === 200,
    'has valid intentId': (r) => r.json('data.intentId') !== undefined,
  });

  try {
    const json = res.json();
    return { intentId: json?.data?.intentId };
  } catch (e) {
    return null;
  }
}

export function deleteIntent(token, intentId) {
  const res = http.del(`${BASE_URL}/api/v1/exchange/intents/${intentId}`, null, {
    headers: { Authorization: `Bearer ${token}` },
    tags: { name: 'DELETE_intent' },
  });
  check(res, {
    'delete intent status is 200': (r) => r.status === 200,
    'intent is deleted': (r) => r.json('data.deleted') === true,
  });
  return res;
}

// ============================================================
// Main (Polling) API
// ============================================================

export function getMain(token) {
  const res = http.get(`${BASE_URL}/api/v1/exchange/main`, {
    headers: { Authorization: `Bearer ${token}` },
    tags: { name: 'GET_main' },
  });
  check(res, {
    'main status is 200': (r) => r.status === 200,
    'has valid main data': (r) => r.json('data') !== undefined,
  });

  try {
    const json = res.json();
    return {
      myIntents: json?.data?.myIntents || [],
      myRooms: json?.data?.myRooms || [],
      recentIntents: json?.data?.recentIntents || [],
    };
  } catch (e) {
    return null;
  }
}

export function getRecentIntents(token, lastIntentId = 0, limit = 10) {
  let url = `${BASE_URL}/api/v1/exchange/intents/recent?limit=${limit}`;
  if (lastIntentId && lastIntentId > 0) {
    url += `&lastIntentId=${lastIntentId}`;
  }
  const res = http.get(url, {
    headers: { Authorization: `Bearer ${token}` },
    tags: { name: 'GET_recent_intents' },
  });
  check(res, {
    'recent intents status is 200': (r) => r.status === 200,
    'has valid intents array': (r) => Array.isArray(r.json('data.intents')),
  });
  return res;
}

// ============================================================
// Room API
// ============================================================

export function getMessages(token, roomId, lastMessageId = null, size = 20) {
  let url = `${BASE_URL}/api/v1/exchange/rooms/${roomId}/messages?size=${size}`;
  if (lastMessageId !== null && lastMessageId !== undefined) {
    url += `&lastMessageId=${lastMessageId}`;
  }
  const res = http.get(url, {
    headers: { Authorization: `Bearer ${token}` },
    tags: { name: 'GET_messages' },
  });
  check(res, {
    'get messages status is 200': (r) => r.status === 200,
    'has valid messages array': (r) => Array.isArray(r.json('data.messages')),
  });

  try {
    const json = res.json();
    return { messages: json?.data?.messages || [] };
  } catch (e) {
    return null;
  }
}

export function sendMessage(token, roomId, content) {
  const res = http.post(
    `${BASE_URL}/api/v1/exchange/rooms/${roomId}/messages`,
    JSON.stringify({ content }),
    {
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      tags: { name: 'POST_message' },
    }
  );
  check(res, {
    'send message status is 201/200': (r) => r.status === 201 || r.status === 200,
    'has valid messageId': (r) => r.json('data.messageId') !== undefined,
  });
  return res;
}

export function toggleRoom(token, roomId, on) {
  const res = http.patch(
    `${BASE_URL}/api/v1/exchange/rooms/${roomId}/toggle`,
    JSON.stringify({ on }),
    {
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      tags: { name: 'PATCH_toggle' },
    }
  );
  check(res, {
    'toggle room status is 200': (r) => r.status === 200,
    'has valid room toggle status': (r) => r.json('data.on') !== undefined,
  });
  return res;
}

// ============================================================
// Course Number Generator
// ============================================================

/**
 * 랜덤한 학수번호 생성 (5자리 숫자)
 */
export function getRandomCourseNo() {
  const prefixes = ['10', '20', '30', '40', '50'];
  const prefix = prefixes[Math.floor(Math.random() * prefixes.length)];
  const suffix = String(Math.floor(Math.random() * 900) + 100);
  return `${prefix}${suffix}`;
}

/**
 * 교환 가능한 과목 번호 쌍 생성
 */
export function getCoursePair() {
  const courses = [
    { give: '10023', want: '40101' },
    { give: '20011', want: '30055' },
    { give: '30122', want: '10042' },
    { give: '40012', want: '20441' },
    { give: '50201', want: '10023' },
  ];
  return courses[Math.floor(Math.random() * courses.length)];
}

