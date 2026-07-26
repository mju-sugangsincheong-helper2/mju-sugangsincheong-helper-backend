/**
 * Exchange 부하 프로필
 *
 * small:  기능 검증 수준 (10 VU)
 * middle: 일반 부하 시뮬레이션 (30 VU)
 * large:  부하 테스트 (100 VU)
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
 * 시나리오별 threshold (SLA 성능 목표 및 에러율 기준)
 * docs/exchange/load_performance_test.md 기준:
 * - 메인 5초 폴링 조회 SLA: 200ms 이내
 * - Intent 등록/철회 및 그래프 탐색 SLA: 500ms 이내
 * - 대화방 메시지/토글 SLA: 300ms 이내
 */
export const thresholds = {
  main: {
    http_req_duration: ['p(95)<200'],
    http_req_failed: ['rate<0.01'],
  },
  intent: {
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
  },
  room: {
    http_req_duration: ['p(95)<300'],
    http_req_failed: ['rate<0.01'],
  },
  'full-flow': {
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.02'],
  },
};

