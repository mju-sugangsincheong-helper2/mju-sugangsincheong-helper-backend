/**
 * Multigame - Result 시나리오
 *
 * 결과 조회, 통계, 대시보드 등 읽기 전용 API에 부하.
 * 게임 종료 후 유저들이 결과를 확인하는 상황 시뮬레이션.
 */
import { sleep } from 'k6';
import { guestLogin } from '../../common/login.js';
import { profiles, thresholds } from '../load-profiles.js';
import {
  getMyHistory,
  getMyStats,
  getDashboard,
  getDepartmentParticipationStats,
  getDepartmentSuccessRateStats,
  computeActiveMultigameId,
  getGameResult,
} from '../helpers.js';

const tier = __ENV.LOAD_TIER || 'small';
export const options = {
  ...profiles[tier],
  thresholds: thresholds.result,
};

export default function () {
  const token = guestLogin();
  if (!token) return;

  // 1. 내 참여 기록 조회 (페이징)
  getMyHistory(token, 0, 10);
  sleep(0.3);

  // 2. 내 참여 통계 요약
  getMyStats(token);
  sleep(0.3);

  // 3. 대시보드 조회
  getDashboard(token);
  sleep(0.3);

  // 4. 학과별 통계 조회
  getDepartmentParticipationStats(token);
  sleep(0.3);

  getDepartmentSuccessRateStats(token);
  sleep(0.3);

  // 5. 특정 게임 결과 조회 (현재 액티브 게임)
  const multigameId = computeActiveMultigameId();
  getGameResult(token, multigameId);
}
