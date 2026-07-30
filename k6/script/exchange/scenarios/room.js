/**
 * Exchange - Room 시나리오
 *
 * 채팅방 관련 동작: 메시지 조회, 전송, 토글.
 * 방이 존재하지 않으면 404가 반환되지만 부하 패턴은 유효.
 */
import { sleep } from 'k6';
import { createTestMember } from '../../common/login.js';
import { profiles, thresholds } from '../load-profiles.js';
import { getMessages, sendMessage, toggleRoom, getMain, getRecentIntents } from '../helpers.js';

const tier = __ENV.LOAD_TIER || 'small';
export const options = {
  ...profiles[tier],
  thresholds: thresholds.room,
};

export function setup() {
  const token = createTestMember();
  return { token };
}

export default function (data) {
  const token = createTestMember() || data?.token;
  if (!token) return;

  // 1. 메인 화면에서 내 방 목록 확인
  const mainData = getMain(token);
  sleep(0.5);

  // 방이 있으면 해당 방에 대해 동작
  if (mainData && mainData.myRooms && mainData.myRooms.length > 0) {
    const room = mainData.myRooms[0];
    const roomId = room.roomId;

    // 2. 메시지 조회 (읽음 처리 스키마 자동 갱신)
    getMessages(token, roomId);
    sleep(0.5);

    // 3. 메시지 전송
    sendMessage(token, roomId, '부하 테스트 메시지입니다.');
    sleep(0.5);

    // 4. 토글 (OFF → ON)
    toggleRoom(token, roomId, false);
    sleep(0.3);
    toggleRoom(token, roomId, true);
    sleep(0.5);
  } else {
    // 방이 없으면 최근 교환 의사 피드 조회
    getRecentIntents(token);
    sleep(0.5);
  }
}
