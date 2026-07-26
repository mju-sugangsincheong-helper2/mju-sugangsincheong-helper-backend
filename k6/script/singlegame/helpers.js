import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// ============================================================
// Game Save API
// ============================================================

export function saveGame(token, options = {}) {
  const totalCourses = options.totalCourses || 6;
  const tEnterMain = options.tEnterMain || 1000;

  const details = [];
  for (let i = 1; i <= totalCourses; i++) {
    details.push({
      sequence: i,
      tClickCourse: 300,
      tClickYes: 200,
      tClickOk: 200,
    });
  }

  const payload = JSON.stringify({
    totalCourses,
    isCompleted: true,
    tEnterMain,
    details,
  });

  const res = http.post(`${BASE_URL}/api/v1/singlegame`, payload, {
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    tags: { name: 'POST_singlegame' },
  });
  check(res, {
    'game save status is 201/200': (r) => r.status === 201 || r.status === 200,
    'has valid gameId': (r) => r.json('data.gameId') !== undefined || r.json('data.id') !== undefined,
  });

  try {
    const json = res.json();
    return { gameId: json?.data?.gameId || json?.data?.id };
  } catch (e) {
    return null;
  }
}

// ============================================================
// Ranking API
// ============================================================

export function getRankings(token, totalCourses = 6, scope = 'GLOBAL') {
  const res = http.get(
    `${BASE_URL}/api/v1/singlegame/rank?totalCourses=${totalCourses}&scope=${scope}`,
    {
      headers: { Authorization: `Bearer ${token}` },
      tags: { name: 'GET_rank' },
    }
  );
  check(res, {
    'get rankings status is 200': (r) => r.status === 200,
    'has valid rank data': (r) => r.json('data') !== undefined,
  });
  return res;
}

// ============================================================
// My Records API
// ============================================================

export function getMyRecords(token, page = 0, size = 10) {
  const res = http.get(`${BASE_URL}/api/v1/singlegame/my?page=${page}&size=${size}`, {
    headers: { Authorization: `Bearer ${token}` },
    tags: { name: 'GET_my_records' },
  });
  check(res, {
    'get my records status is 200': (r) => r.status === 200,
    'has valid my records data': (r) => r.json('data') !== undefined,
  });
  return res;
}

// ============================================================
// Analysis API
// ============================================================

export function getAnalysis(token, gameId) {
  const res = http.get(`${BASE_URL}/api/v1/singlegame/${gameId}/analysis`, {
    headers: { Authorization: `Bearer ${token}` },
    tags: { name: 'GET_analysis' },
  });
  check(res, {
    'get analysis status is 200/404': (r) => r.status === 200 || r.status === 404,
    'has valid analysis response': (r) => r.status === 404 || r.json('data') !== undefined,
  });
  return res;
}

