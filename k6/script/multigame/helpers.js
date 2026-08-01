import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// ============================================================
// Reservation API
// ============================================================

export function createReservation(token, multigameId) {
  const res = http.post(
    `${BASE_URL}/api/v1/multigame/reservations`,
    JSON.stringify({ multigameId }),
    {
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      tags: { name: 'POST_reservations' },
    }
  );
  check(res, {
    'reservation created': (r) => r.status === 200 || r.status === 201 || r.status === 409,
  });
  return res;
}

export function getMyReservations(token) {
  const res = http.get(`${BASE_URL}/api/v1/multigame/reservations/my`, {
    headers: { Authorization: `Bearer ${token}` },
    tags: { name: 'GET_reservations_my' },
  });
  check(res, { 'my reservations fetched': (r) => r.status === 200 });
  return res;
}

// ============================================================
// Session API (대기방 + 게임 신청)
// ============================================================

export function enterWaitingRoom(token) {
  const res = http.get(`${BASE_URL}/api/v1/multigame/session/waiting-room`, {
    headers: { Authorization: `Bearer ${token}` },
    tags: { name: 'GET_waiting_room' },
  });
  const success = res.status === 200 || res.status === 410;
  check(res, { 'waiting room entered': success });

  try {
    const json = res.json();
    return {
      state: json?.data?.state,
      participation: json?.data?.participation,
      multigameId: json?.data?.multigameId,
    };
  } catch (e) {
    return null;
  }
}

export function requestGame(token, subjectId) {
  const res = http.post(
    `${BASE_URL}/api/v1/multigame/session/request?subjectId=${subjectId}`,
    null,
    {
      headers: { Authorization: `Bearer ${token}` },
      tags: { name: 'POST_game_request' },
    }
  );
  check(res, { 'game request sent': (r) => r.status === 200 });

  try {
    const json = res.json();
    return {
      status: json?.data?.status,
      seq: json?.data?.seq,
      limit: json?.data?.limit,
      subjectId: json?.data?.subjectId,
    };
  } catch (e) {
    return null;
  }
}

// ============================================================
// Lifecycle Control API (dev 환경 전용)
// ============================================================

/**
 * 게임 상태 수동 전이 (dev 환경 전용)
 * ADMIN 권한이 필요하며, dev 프로파일에서만 동작합니다.
 */
export function transitionGameState(adminToken, multigameId, targetState) {
  const res = http.post(
    `${BASE_URL}/api/v1/multigame/lifecycle/transition/${multigameId}?targetState=${targetState}`,
    null,
    {
      headers: { Authorization: `Bearer ${adminToken}` },
      tags: { name: 'POST_lifecycle_transition' },
    }
  );
  check(res, { 'state transitioned': (r) => r.status === 200 });
  return res;
}

/**
 * 게임 상태 조회 (dev 환경 전용)
 */
export function getGameState(adminToken, multigameId) {
  const res = http.get(`${BASE_URL}/api/v1/multigame/lifecycle/state/${multigameId}`, {
    headers: { Authorization: `Bearer ${adminToken}` },
    tags: { name: 'GET_lifecycle_state' },
  });
  check(res, { 'game state fetched': (r) => r.status === 200 });

  try {
    const json = res.json();
    return json?.data;
  } catch (e) {
    return null;
  }
}

// ============================================================
// Result API
// ============================================================

// 결과 상세(분석서 + 내 결과 + 내 신청 로그 통합) 조회
// GET /api/v1/multigame/results/{multigameId}
export function getGameResult(token, multigameId) {
  const res = http.get(`${BASE_URL}/api/v1/multigame/results/${multigameId}`, {
    headers: { Authorization: `Bearer ${token}` },
    tags: { name: 'GET_round_detail' },
  });
  check(res, { 'round detail fetched': (r) => r.status === 200 || r.status === 404 });
  return res;
}

// ============================================================
// My History API
// ============================================================

// 내 참여 이력 목록 (페이징)
// GET /api/v1/multigame/me/results?page=&size=
export function getMyHistory(token, page = 0, size = 10) {
  const res = http.get(`${BASE_URL}/api/v1/multigame/me/results?page=${page}&size=${size}`, {
    headers: { Authorization: `Bearer ${token}` },
    tags: { name: 'GET_me_results' },
  });
  check(res, { 'my history fetched': (r) => r.status === 200 });
  return res;
}

export function getMyStats(token) {
  const res = http.get(`${BASE_URL}/api/v1/multigame/my/stats`, {
    headers: { Authorization: `Bearer ${token}` },
    tags: { name: 'GET_my_stats' },
  });
  check(res, { 'my stats fetched': (r) => r.status === 200 });
  return res;
}

// ============================================================
// Dashboard & Stats API
// ============================================================

export function getDashboard(token) {
  const res = http.get(`${BASE_URL}/api/v1/multigame/dashboard`, {
    headers: { Authorization: `Bearer ${token}` },
    tags: { name: 'GET_dashboard' },
  });
  check(res, { 'dashboard fetched': (r) => r.status === 200 });
  return res;
}

export function getDepartmentParticipationStats(token) {
  const res = http.get(`${BASE_URL}/api/v1/multigame/stats/department/participation`, {
    headers: { Authorization: `Bearer ${token}` },
    tags: { name: 'GET_dept_participation' },
  });
  check(res, { 'dept participation fetched': (r) => r.status === 200 });
  return res;
}

export function getDepartmentSuccessRateStats(token) {
  const res = http.get(`${BASE_URL}/api/v1/multigame/stats/department/success-rate`, {
    headers: { Authorization: `Bearer ${token}` },
    tags: { name: 'GET_dept_success_rate' },
  });
  check(res, { 'dept success rate fetched': (r) => r.status === 200 });
  return res;
}

// ============================================================
// Utility
// ============================================================

/**
 * 예약 가능한 게임 ID 계산 (현재 시각 + 10분 이후의 다음 10분 마크)
 * 예약은 게임 시작 10분 전까지 생성 가능하므로,
 * 최소 10분 이후의 10분 마크를 반환합니다.
 */
export function computeReservableMultigameId() {
  const now = new Date();
  // 최소 10분 이후이므로, 현재 + 10분에서 다음 10분 마크를 계산
  now.setMinutes(now.getMinutes() + 10);
  const minutes = now.getMinutes();
  const tenMark = Math.ceil(minutes / 10) * 10;
  if (tenMark > minutes) {
    now.setMinutes(tenMark);
  } else {
    now.setMinutes(tenMark + 10);
  }
  now.setSeconds(0);
  now.setMilliseconds(0);

  return formatMultigameId(now);
}

/**
 * 서버의 computeActiveGameT()와 동일한 로직으로 현재 액티브 게임의 T를 계산.
 * minute % 10 >= 5: 다음 10분 마크 (ceiling) — [T-5m, T) 구간
 * minute % 10 < 5:  현재 10분 마크 (floor)   — [T, T+5m) 구간
 */
export function computeActiveMultigameId() {
  const now = new Date();
  const minutes = now.getMinutes();
  const tenMark = Math.floor(minutes / 10) * 10;

  if (minutes % 10 >= 5) {
    now.setMinutes(tenMark + 10);
  } else {
    now.setMinutes(tenMark);
  }
  now.setSeconds(0);
  now.setMilliseconds(0);

  return formatMultigameId(now);
}

function formatMultigameId(date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  const hour = String(date.getHours()).padStart(2, '0');
  const min = String(date.getMinutes()).padStart(2, '0');
  return `${year}${month}${day}${hour}${min}00`;
}

export function getRandomSubjectId() {
  return Math.floor(Math.random() * 6) + 1;
}
