/**
 * Exchange - Intent 시나리오
 *
 * 교환 의사(Intent) 등록 + 철회에 집중.
 * 사이클 탐색 엔진에 부하를 주는 시나리오.
 */
import { sleep } from 'k6';
import { createTestMember } from '../../common/login.js';
import { profiles, thresholds } from '../load-profiles.js';
import { createIntent, deleteIntent, getMain, getCoursePair } from '../helpers.js';

const tier = __ENV.LOAD_TIER || 'small';
export const options = {
  ...profiles[tier],
  thresholds: thresholds.intent,
};

export function setup() {
  const token = createTestMember();
  return { token };
}

export default function (data) {
  const token = createTestMember() || data?.token;
  if (!token) return;

  // 1. 메인 화면 조회 (현재 내 의도 확인)
  const mainData = getMain(token);
  sleep(0.5);

  // 2. 교환 의사 등록 (동시 등록 및 그래프 탐색 부하)
  const pair = getCoursePair();
  const result = createIntent(token, pair.give, pair.want);
  sleep(1);

  // 3. 메인 화면 재조회 (등록 반영 확인)
  getMain(token);
  sleep(0.5);

  // 4. 의사가 성공적으로 등록되었으면 철회
  if (result && result.intentId) {
    deleteIntent(token, result.intentId);
    sleep(0.5);
  }
}

