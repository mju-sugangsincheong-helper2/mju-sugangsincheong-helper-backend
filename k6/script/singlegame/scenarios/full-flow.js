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

  // 다양한 과목 수로 게임 저장 (실제 유저 패턴 시뮬레이션)
  const courseOptions = [1, 3, 6, 6, 6, 7, 8]; // 6과목이 가장 많음
  const totalCourses = courseOptions[Math.floor(Math.random() * courseOptions.length)];
  
  const saveResult = saveGame(token, { totalCourses });
  sleep(randomSleep(0.5, 2));

  // 전체 랭킹 확인 (다양한 과목 수 조회)
  const rankCourseOptions = [1, 3, 6, 7, 8];
  const rankCourses = rankCourseOptions[Math.floor(Math.random() * rankCourseOptions.length)];
  getRankings(token, rankCourses, 'GLOBAL');
  sleep(randomSleep(0.3, 1));

  // 학과 랭킹 확인
  getRankings(token, rankCourses, 'DEPARTMENT');
  sleep(randomSleep(0.3, 1));

  // 내 기록 확인 (다양한 페이지)
  const page = Math.floor(Math.random() * 3); // 0, 1, 2 페이지
  getMyRecords(token, page, 10);
  sleep(randomSleep(0.3, 1));

  // 방금 저장한 게임 상세 분석 (3영역: basic, detail, feedbacks)
  if (saveResult && saveResult.gameId) {
    const analysisRes = getAnalysis(token, saveResult.gameId);
    if (analysisRes.status === 200) {
      const analysisData = analysisRes.json('data');
      if (analysisData) {
        console.log(`Analysis: totalTime=${analysisData.totalTime}ms, feedbacks.primary=${analysisData.feedbacks?.primary?.code}`);
      }
    }
  }
}

function randomSleep(min, max) {
  return min + Math.random() * (max - min);
}

