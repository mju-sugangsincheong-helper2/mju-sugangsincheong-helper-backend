/**
 * Singlegame - Save 시나리오
 *
 * 게임 결과 저장에 집중.
 * 가장 높은 write 부하가 발생하는 시나리오.
 */
import { sleep } from 'k6';
import { guestLogin } from '../../common/login.js';
import { profiles, thresholds } from '../load-profiles.js';
import { saveGame } from '../helpers.js';

const tier = __ENV.LOAD_TIER || 'small';
export const options = {
  ...profiles[tier],
  thresholds: thresholds.save,
};

export function setup() {
  const token = guestLogin();
  return { token };
}

export default function (data) {
  const token = guestLogin() || data?.token;
  if (!token) return;

  // 다양한 과목 수(3, 6, 8)로 게임 저장 (정각 스파이크 쓰기 부하)
  const totalCourses = [3, 6, 6, 6, 8][Math.floor(Math.random() * 5)];
  saveGame(token, { totalCourses });
  sleep(0.5);
}

