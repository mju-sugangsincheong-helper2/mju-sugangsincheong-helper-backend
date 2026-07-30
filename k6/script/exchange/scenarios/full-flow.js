/**
 * Exchange - Full Flow 시나리오
 *
 * 메인 폴링 → 의사 등록 → 메인 재조회 → 방 확인 → 메시지 → 토글 전체 플로우.
 * 실제 유저 행동을 가장 가까이 시뮬레이션.
 */
import { sleep } from 'k6';
import { createTestMember } from '../../common/login.js';
import { profiles, thresholds } from '../load-profiles.js';
import {
  getMain,
  getRecentIntents,
  createIntent,
  deleteIntent,
  getMessages,
  sendMessage,
  toggleRoom,
  getCoursePair,
} from '../helpers.js';

const tier = __ENV.LOAD_TIER || 'small';
export const options = {
  ...profiles[tier],
  thresholds: thresholds['full-flow'],
};

export function setup() {
  const token = createTestMember();
  return { token };
}

export default function (data) {
  const token = createTestMember() || data?.token;
  if (!token) return;

  // 1. 메인 화면 조회
  const mainData = getMain(token);
  sleep(1);

  // 2. 최근 교환 의사 피드 확인
  getRecentIntents(token);
  sleep(0.5);

  // 3. 교환 의사 등록
  const pair = getCoursePair();
  const intentResult = createIntent(token, pair.give, pair.want);
  sleep(1);

  // 4. 메인 화면 재조회 (방 생성 확인)
  const updatedMain = getMain(token);
  sleep(0.5);

  // 5. 방이 있으면 메시지 동작
  if (updatedMain && updatedMain.myRooms && updatedMain.myRooms.length > 0) {
    const room = updatedMain.myRooms[0];
    const roomId = room.roomId;

    getMessages(token, roomId);
    sleep(0.5);

    sendMessage(token, roomId, '안녕하세요, 교환하실 분 계신가요?');
    sleep(0.5);

    toggleRoom(token, roomId, false);
    sleep(0.3);
    toggleRoom(token, roomId, true);
    sleep(0.5);
  }

  // 6. 의사 철회
  if (intentResult && intentResult.intentId) {
    deleteIntent(token, intentResult.intentId);
    sleep(0.5);
  }

  // 7. 최종 메인 확인
  getMain(token);
}
