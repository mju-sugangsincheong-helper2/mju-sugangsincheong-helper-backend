/**
 * Singlegame - Rank 시나리오
 *
 * 랭킹 조회 + 내 기록 조회에 집중.
 * 읽기 전용 부하.
 */
import { sleep } from 'k6';
import { guestLogin } from '../../common/login.js';
import { profiles, thresholds } from '../load-profiles.js';
import { getRankings, getMyRecords, getAnalysis } from '../helpers.js';

const tier = __ENV.LOAD_TIER || 'small';
export const options = {
  ...profiles[tier],
  thresholds: thresholds.rank,
};

export function setup() {
  const token = guestLogin();
  return { token };
}

export default function (data) {
  const token = guestLogin() || data?.token;
  if (!token) return;

  // 1. 전체 랭킹 조회 (GLOBAL)
  getRankings(token, 6, 'GLOBAL');
  sleep(0.3);

  // 2. 학과 랭킹 조회 (DEPARTMENT)
  getRankings(token, 6, 'DEPARTMENT');
  sleep(0.3);

  // 3. 내 기록 조회
  getMyRecords(token, 0, 10);
  sleep(0.3);

  // 4. 다른 과목 수 랭킹도 조회
  getRankings(token, 3, 'GLOBAL');
  sleep(0.3);
}

