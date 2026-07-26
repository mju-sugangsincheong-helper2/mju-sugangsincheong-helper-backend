/**
 * Multigame - Reservation 시나리오
 *
 * 예약 생성 + 내 예약 목록 조회에 집중.
 * dev 환경에서는 예약 생성 시 자동으로 WAITING 상태 초기화.
 */
import { sleep } from 'k6';
import { guestLogin } from '../../common/login.js';
import { profiles, thresholds } from '../load-profiles.js';
import {
  createReservation,
  getMyReservations,
  computeReservableMultigameId,
} from '../helpers.js';

const tier = __ENV.LOAD_TIER || 'small';
export const options = {
  ...profiles[tier],
  thresholds: thresholds.reservation,
};

export default function () {
  const token = guestLogin();
  if (!token) return;

  // 예약은 최소 10분 이후의 게임에 대해 생성
  const multigameId = computeReservableMultigameId();

  // 예약 생성
  createReservation(token, multigameId);
  sleep(0.3);

  // 내 예약 목록 조회
  getMyReservations(token);
  sleep(0.5);
}
