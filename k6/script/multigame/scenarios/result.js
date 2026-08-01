/**
 * Multigame - Result 시나리오
 *
 * 내 참여 기록, 학과 랭킹, 라운드 상세 등 읽기 전용 API에 부하.
 * 게임 종료 후 유저들이 결과를 확인하는 상황 시뮬레이션.
 */
import { sleep } from 'k6';
import { guestLogin } from '../../common/login.js';
import { profiles, thresholds } from '../load-profiles.js';
import {
  getMyHistory,
  getRankings,
  getGameResult,
  computeActiveMultigameId,
} from '../helpers.js';

const tier = __ENV.LOAD_TIER || 'small';
export const options = {
  ...profiles[tier],
  thresholds: thresholds.result,
};

export default function () {
  const token = guestLogin();
  if (!token) return;

  // 1. 내 참여 기록 조회 (라운드 단위 + 과목별 results 배열, 페이징)
  getMyHistory(token, 0, 10);
  sleep(0.3);

  // 2. 학과 랭킹 조회 (참가 수 + 상위 70% 평균 성공률)
  getRankings(token);
  sleep(0.3);

  // 3. 마지막으로 종료된 라운드 상세 조회 (게임 진행 중이면 404 허용)
  const multigameId = computeActiveMultigameId();
  getGameResult(token, multigameId);
}
