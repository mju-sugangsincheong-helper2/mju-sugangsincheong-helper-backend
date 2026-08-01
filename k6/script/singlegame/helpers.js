import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// ============================================================
// Random Value Generators
// ============================================================

function randomInt(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

// 유저 반응속도 시뮬레이션 (정규분포 형태)
function randomReactionTime(min, max, peak) {
  const range = max - min;
  const normalized = (Math.random() + Math.random() + Math.random()) / 3;
  return Math.floor(min + range * normalized * (peak / 100));
}

// ============================================================
// Game Save API
// ============================================================

export function saveGame(token, options = {}) {
  const totalCourses = options.totalCourses || 6;
  
  // 정각 진입 반응속도: 200~3000ms (대부분 500~1500ms)
  const tEnterMain = options.tEnterMain || randomReactionTime(200, 3000, 60);

  const details = [];
  for (let i = 1; i <= totalCourses; i++) {
    // 과목 조준 시간: 100~2000ms (대부분 300~800ms)
    const tClickCourse = randomReactionTime(100, 2000, 50);
    // 팝업 확인 시간: 50~800ms (대부분 100~400ms)
    const tClickYes = randomReactionTime(50, 800, 40);
    // 완료 확인 시간: 50~800ms (대부분 100~400ms)
    const tClickOk = randomReactionTime(50, 800, 40);
    
    details.push({
      sequence: i,
      tClickCourse,
      tClickYes,
      tClickOk,
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
    'has basic': (r) => r.status === 404 || r.json('data.basic') !== undefined,
    'has detail': (r) => r.status === 404 || r.json('data.detail') !== undefined,
    'has feedbacks': (r) => r.status === 404 || r.json('data.feedbacks') !== undefined,
    'has ranking': (r) => r.status === 404 || r.json('data.ranking') !== undefined,
  });
  return res;
}

