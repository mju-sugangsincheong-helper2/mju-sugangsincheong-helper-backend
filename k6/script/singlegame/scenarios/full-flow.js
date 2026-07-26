/**
 * Singlegame - Full Flow 시나리오
 *
 * 게임 저장 → 랭킹 조회 → 내 기록 조회 전체 플로우.
 * 실제 유저 행동 시뮬레이션.
 */
import { sleep } from 'k6';
import { guestLogin } from '../../common/login.js';
import { profiles, thresholds } from '../load-profiles.js';
import { saveGame, getRankings, getMyRecords, getAnalysis } from '../helpers.js';

const tier = __ENV.LOAD_TIER || 'small';
export const options = {
  ...profiles[tier],
  thresholds: thresholds['full-flow'],
};

export function setup() {
  const token = guestLogin();
  return { token };
}

export default function (data) {
  const token = guestLogin() || data?.token;
  if (!token) return;

  // 1. 게임 결과 저장
  const saveResult = saveGame(token, { totalCourses: 6 });
  sleep(1);

  // 2. 전체 랭킹 확인
  getRankings(token, 6, 'GLOBAL');
  sleep(1);

  // 3. 학과 랭킹 확인
  getRankings(token, 6, 'DEPARTMENT');
  sleep(0.5);

  // 4. 내 기록 확인
  getMyRecords(token, 0, 10);
  sleep(0.5);

  // 5. 방금 저장한 게임 분석 (gameId가 있으면)
  if (saveResult && saveResult.gameId) {
    getAnalysis(token, saveResult.gameId);
  }
}

