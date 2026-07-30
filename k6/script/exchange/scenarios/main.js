/**
 * Exchange - Main 시나리오
 *
 * 메인 화면 폴링에 집중.
 * 실제 클라이언트는 5초 간격으로 GET /exchange/main을 호출.
 * 부하 테스트에서는 sleep을 줄여 더 빠른 폴링으로 시뮬레이션.
 */
import { sleep } from 'k6';
import { createTestMember } from '../../common/login.js';
import { profiles, thresholds } from '../load-profiles.js';
import { getMain, getRecentIntents } from '../helpers.js';

const tier = __ENV.LOAD_TIER || 'small';
export const options = {
  ...profiles[tier],
  thresholds: thresholds.main,
};

export function setup() {
  const token = createTestMember();
  return { token };
}

export default function (data) {
  const token = createTestMember() || data?.token;
  if (!token) return;

  // 메인 화면 5초 주기 폴링 모사 (피크타임 5초 폴링 스톰)
  for (let i = 0; i < 3; i++) {
    getMain(token);
    sleep(5);
  }

  // 최근 교환 의사 피드 조회
  getRecentIntents(token);
  sleep(1);
}
