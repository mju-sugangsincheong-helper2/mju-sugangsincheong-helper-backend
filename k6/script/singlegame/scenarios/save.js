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

  // 다양한 과목수로 게임 저장 (실제 유저 패턴 시뮬레이션)
  // 1, 3, 6, 7, 8 중 6이 가장 많이 선택됨
  const courseOptions = [1, 3, 6, 6, 6, 6, 7, 8];
  const totalCourses = courseOptions[Math.floor(Math.random() * courseOptions.length)];
  
  saveGame(token, { totalCourses });
  sleep(randomSleep(0.3, 1.5));
}

function randomSleep(min, max) {
  return min + Math.random() * (max - min);
}

