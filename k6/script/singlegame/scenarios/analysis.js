/**
 * Singlegame - Analysis 시나리오
 *
 * 게임 상세 분석 조회에 집중.
 * 3영역(basic, detail, feedbacks) 응답 부하.
 */
import { sleep } from 'k6';
import { guestLogin } from '../../common/login.js';
import { profiles, thresholds } from '../load-profiles.js';
import { saveGame, getAnalysis } from '../helpers.js';

const tier = __ENV.LOAD_TIER || 'small';
export const options = {
  ...profiles[tier],
  thresholds: thresholds.analysis,
};

export function setup() {
  const token = guestLogin();
  return { token };
}

export default function (data) {
  const token = guestLogin() || data?.token;
  if (!token) return;

  // 다양한 과목 수로 게임 저장 후 분석
  const courseOptions = [1, 3, 6, 6, 6, 7, 8]; // 6과목이 가장 많음
  const totalCourses = courseOptions[Math.floor(Math.random() * courseOptions.length)];
  
  const saveResult = saveGame(token, { totalCourses });
  sleep(randomSleep(0.3, 1));

  if (saveResult && saveResult.gameId) {
    const analysisRes = getAnalysis(token, saveResult.gameId);
    if (analysisRes.status === 200) {
      const analysisData = analysisRes.json('data');
      if (analysisData) {
        console.log(`Analysis: courses=${totalCourses}, totalTime=${analysisData.totalTime}ms, feedback=${analysisData.feedbacks?.primary?.code}`);
      }
    }
  }
}

function randomSleep(min, max) {
  return min + Math.random() * (max - min);
}
