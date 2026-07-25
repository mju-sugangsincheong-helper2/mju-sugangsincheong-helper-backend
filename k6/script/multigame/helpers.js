import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export function createReservation(token, multigameId) {
  const params = {
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    tags: { name: 'POST_reservations' },
  };

  const payload = JSON.stringify({ multigameId });
  const res = http.post(`${BASE_URL}/api/v1/multigame/reservations`, payload, params);

  check(res, {
    'reservation created': (r) => r.status === 200 || r.status === 201 || r.status === 409,
  });

  return res;
}

export function getMyReservations(token) {
  const params = {
    headers: {
      'Authorization': `Bearer ${token}`,
    },
    tags: { name: 'GET_reservations_my' },
  };

  const res = http.get(`${BASE_URL}/api/v1/multigame/reservations/my`, params);
  check(res, { 'my reservations fetched': (r) => r.status === 200 });
  return res;
}

export function getAllReservations(token, multigameId = null) {
  const params = {
    headers: {
      'Authorization': `Bearer ${token}`,
    },
    tags: { name: 'GET_reservations' },
  };

  let url = `${BASE_URL}/api/v1/multigame/reservations`;
  if (multigameId) {
    url += `?multigameId=${multigameId}`;
  }

  const res = http.get(url, params);
  check(res, { 'all reservations fetched': (r) => r.status === 200 });
  return res;
}

export function enterWaitingRoom(token) {
  const params = {
    headers: {
      'Authorization': `Bearer ${token}`,
    },
    tags: { name: 'POST_waiting_room' },
  };

  const res = http.post(`${BASE_URL}/api/v1/multigame/session/waiting-room`, null, params);
  
  // 200 또는 410 (게임 취소) 모두 정상으로 처리
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
  const params = {
    headers: {
      'Authorization': `Bearer ${token}`,
    },
    tags: { name: 'POST_game_request' },
  };

  const res = http.post(
    `${BASE_URL}/api/v1/multigame/session/request?subjectId=${subjectId}`,
    null,
    params
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

export function getGameResult(token, multigameId) {
  const params = {
    headers: {
      'Authorization': `Bearer ${token}`,
    },
    tags: { name: 'GET_results' },
  };

  const res = http.get(`${BASE_URL}/api/v1/multigame/results/${multigameId}`, params);
  check(res, { 'game result fetched': (r) => r.status === 200 || r.status === 404 });
  return res;
}

export function getMyResult(token, multigameId) {
  const params = {
    headers: {
      'Authorization': `Bearer ${token}`,
    },
    tags: { name: 'GET_results_my' },
  };

  const res = http.get(`${BASE_URL}/api/v1/multigame/results/my?multigameId=${multigameId}`, params);
  check(res, { 'my result fetched': (r) => r.status === 200 || r.status === 404 });
  return res;
}

export function computeNextMultigameId() {
  // 테스트용으로 1일 후의 10분 마크를 계산
  const now = new Date();
  const tomorrow = new Date(now.getTime() + 24 * 60 * 60 * 1000);
  
  // 10분 마크로 조정 (00분으로 설정)
  tomorrow.setMinutes(0);
  tomorrow.setSeconds(0);
  tomorrow.setMilliseconds(0);

  const year = tomorrow.getFullYear();
  const month = String(tomorrow.getMonth() + 1).padStart(2, '0');
  const day = String(tomorrow.getDate()).padStart(2, '0');
  const hour = String(tomorrow.getHours()).padStart(2, '0');
  const min = String(tomorrow.getMinutes()).padStart(2, '0');
  const sec = '00';

  return `${year}${month}${day}${hour}${min}${sec}`;
}

export function computeMultigameIdForDate(date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  const hour = String(date.getHours()).padStart(2, '0');
  const min = String(date.getMinutes()).padStart(2, '0');
  const sec = '00';

  return `${year}${month}${day}${hour}${min}${sec}`;
}

export function getRandomSubjectId() {
  return Math.floor(Math.random() * 6) + 1;
}
