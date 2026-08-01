import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// ============================================================
// Session API (대기방 + 진입/이탈 + 과목 신청)
// ============================================================

// GET /api/v1/multigame/session/waiting-room
export function enterWaitingRoom(token) {
  const res = http.get(`${BASE_URL}/api/v1/multigame/session/waiting-room`, {
    headers: { Authorization: `Bearer ${token}` },
    tags: { name: 'GET_waiting_room' },
  });
  check(res, { 'waiting room entered': (r) => r.status === 200 });

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

// POST /api/v1/multigame/session/enter
export function enterGame(token) {
  const res = http.post(`${BASE_URL}/api/v1/multigame/session/enter`, null, {
    headers: { Authorization: `Bearer ${token}` },
    tags: { name: 'POST_game_enter' },
  });
  // PROGRESS가 아니면 409, 최소 인원 미달 취소면 410 — 예상된 에러
  check(res, { 'game entered': (r) => r.status === 200 || r.status === 409 || r.status === 410 });

  try {
    const json = res.json();
    return {
      multigameId: json?.data?.multigameId,
      state: json?.data?.state,
      participation: json?.data?.participation,
    };
  } catch (e) {
    return null;
  }
}

// POST /api/v1/multigame/session/leave
export function leaveGame(token) {
  const res = http.post(`${BASE_URL}/api/v1/multigame/session/leave`, null, {
    headers: { Authorization: `Bearer ${token}` },
    tags: { name: 'POST_game_leave' },
  });
  check(res, { 'game left': (r) => r.status === 200 });
  return res;
}

// POST /api/v1/multigame/session/apply?subjectId=
// status: BLOCKED / PENDING(재시도 필요) / SUCCESS / FAIL_SOLDOUT / FAIL_DUPLICATE
export function requestGame(token, subjectId) {
  const res = http.post(
    `${BASE_URL}/api/v1/multigame/session/apply?subjectId=${subjectId}`,
    null,
    {
      headers: { Authorization: `Bearer ${token}` },
      tags: { name: 'POST_game_apply' },
    }
  );
  check(res, { 'game apply sent': (r) => r.status === 200 });

  try {
    const json = res.json();
    return {
      status: json?.data?.status,
      seq: json?.data?.seq,
      limit: json?.data?.limit,
      subjectId: json?.data?.subjectId,
      remaining: json?.data?.remaining,
    };
  } catch (e) {
    return null;
  }
}

// ============================================================
// Result API (결과 상세 + 내 참여 기록 + 랭킹)
// ============================================================

// GET /api/v1/multigame/results/{multigameId} — 라운드 상세(분석서 + 내 결과 + 내 신청 타임라인)
export function getGameResult(token, multigameId) {
  const res = http.get(`${BASE_URL}/api/v1/multigame/results/${multigameId}`, {
    headers: { Authorization: `Bearer ${token}` },
    tags: { name: 'GET_round_detail' },
  });
  check(res, { 'round detail fetched': (r) => r.status === 200 || r.status === 404 });
  return res;
}

// GET /api/v1/multigame/me/results?page=&size= — 라운드 단위 + 과목별 results 배열
export function getMyHistory(token, page = 0, size = 10) {
  const res = http.get(`${BASE_URL}/api/v1/multigame/me/results?page=${page}&size=${size}`, {
    headers: { Authorization: `Bearer ${token}` },
    tags: { name: 'GET_me_results' },
  });
  check(res, { 'my history fetched': (r) => r.status === 200 });
  return res;
}

// GET /api/v1/multigame/rankings — 학과별 참가 수 + 상위 70% 성공률 순위
export function getRankings(token) {
  const res = http.get(`${BASE_URL}/api/v1/multigame/rankings`, {
    headers: { Authorization: `Bearer ${token}` },
    tags: { name: 'GET_rankings' },
  });
  check(res, { 'rankings fetched': (r) => r.status === 200 });
  return res;
}

// ============================================================
// Utility
// ============================================================

/**
 * 서버 RoundTime.currentMark(now)와 동일한 로직으로 현재 10분 윈도우의 라운드 ID(T)를 계산.
 * 라운드 T는 T:00 ~ T:00:30에 진행되고 T+30s에 결과가 영속화되므로,
 * 종료 후 GET /results/{T}로 해당 라운드 결과를 조회할 수 있다.
 */
export function computeActiveMultigameId() {
  const now = new Date();
  now.setMinutes(now.getMinutes() - (now.getMinutes() % 10));
  now.setSeconds(0);
  now.setMilliseconds(0);

  return formatMultigameId(now);
}

/**
 * 1~6 과목 중 랜덤 셔플 후 count개 선택.
 * 한 라운드에서 유저는 과목별로 각각 성공할 수 있으므로(최대 6개),
 * 시나리오에서는 여러 과목을 독립적으로 신청한다.
 */
export function getRandomSubjectIds(count = 3) {
  const subjects = [1, 2, 3, 4, 5, 6];
  for (let i = subjects.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [subjects[i], subjects[j]] = [subjects[j], subjects[i]];
  }
  return subjects.slice(0, Math.min(count, subjects.length));
}

function formatMultigameId(date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  const hour = String(date.getHours()).padStart(2, '0');
  const min = String(date.getMinutes()).padStart(2, '0');
  return `${year}${month}${day}${hour}${min}00`;
}
