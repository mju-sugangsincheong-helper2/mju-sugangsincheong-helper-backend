/**
 * Multigame 부하 프로필
 *
 * small:  기능 검증 수준 (10 VU)
 * middle: 일반 부하 시뮬레이션 (30 VU)
 * large:  부하 테스트 (100 VU)
 *
 * 각 시나리오별 threshold는 해당 시나리오 파일에서 정의.
 */
export const profiles = {
  small: {
    stages: [
      { duration: '30s', target: 10 },
      { duration: '1m', target: 10 },
      { duration: '30s', target: 0 },
    ],
  },
  middle: {
    stages: [
      { duration: '1m', target: 30 },
      { duration: '2m', target: 30 },
      { duration: '1m', target: 0 },
    ],
  },
  large: {
    stages: [
      { duration: '2m', target: 100 },
      { duration: '3m', target: 100 },
      { duration: '2m', target: 0 },
    ],
  },
};

/**
 * 시나리오별 threshold (부하 티어 무관 공통)
 *
 * 참고: http_req_failed threshold는 예상된 HTTP 에러를 고려하여 설정합니다.
 * - 대기방 입장: 게임이 CANCELLED 상태이면 410 (Gone) 반환
 * - 게임 신청: PROGRESS 상태가 아니면 400 응답
 * - 결과 조회: 결과가 없으면 404 반환
 * 이러한 예상된 에러는 전체 요청의 일정 비율을 차지할 수 있습니다.
 */
export const thresholds = {
  reservation: {
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
  },
  session: {
    // 대기방 폴링(3초) + 게임 신청(반복)이므로 p95 기준 느슨하게
    // 게임이 WAITING/READY/CANCELLED 상태이면 예상된 에러 발생
    http_req_duration: ['p(95)<800'],
    http_req_failed: ['rate<0.30'],
  },
  result: {
    http_req_duration: ['p(95)<300'],
    http_req_failed: ['rate<0.10'],
  },
  'full-flow': {
    // 전체 플로우에서 게임 상태에 따른 예상된 에러 포함
    http_req_duration: ['p(95)<1000'],
    http_req_failed: ['rate<0.30'],
  },
};
