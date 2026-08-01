/**
 * Singlegame - Rank 시나리오
 *
 * 랭킹 조회 + 내 기록 조회 + 분석 조회에 집중.
 * 읽기 전용 부하.
 */
import { sleep } from 'k6';
import { guestLogin } from '../../common/login.js';
import { profiles, thresholds } from '../load-profiles.js';
import { saveGame, getRankings, getMyRecords, getAnalysis } from '../helpers.js';

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

  // 다양한 과목 수로 랭킹 조회 (실제 유저 패턴)
  const courseOptions = [1, 3, 6, 6, 6, 7, 8]; // 6과목이 가장 많음
  
  // 1. 전체 랭킹 조회 (GLOBAL) - 랜덤 과목 수
  const courses1 = courseOptions[Math.floor(Math.random() * courseOptions.length)];
  getRankings(token, courses1, 'GLOBAL');
  sleep(randomSleep(0.2, 0.8));

  // 2. 학과 랭킹 조회 (DEPARTMENT) - 다른 과목 수
  const courses2 = courseOptions[Math.floor(Math.random() * courseOptions.length)];
  getRankings(token, courses2, 'DEPARTMENT');
  sleep(randomSleep(0.2, 0.8));

  // 3. 내 기록 조회 (다양한 페이지)
  const page = Math.floor(Math.random() * 5); // 0~4 페이지
  getMyRecords(token, page, 10);
  sleep(randomSleep(0.2, 0.8));

  // 4. 다른 과목 수 랭킹도 조회
  const courses3 = courseOptions[Math.floor(Math.random() * courseOptions.length)];
  getRankings(token, courses3, 'GLOBAL');
  sleep(randomSleep(0.2, 0.8));

  // 5. 게임 저장 후 분석 조회 (읽기 시나리오에 쓰기 포함)
  const saveCourses = courseOptions[Math.floor(Math.random() * courseOptions.length)];
  const saveResult = saveGame(token, { totalCourses: saveCourses });
  if (saveResult && saveResult.gameId) {
    sleep(randomSleep(0.2, 0.5));
    getAnalysis(token, saveResult.gameId);
  }
}

function randomSleep(min, max) {
  return min + Math.random() * (max - min);
}

