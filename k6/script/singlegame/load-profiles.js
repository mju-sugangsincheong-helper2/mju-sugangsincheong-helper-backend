/**
 * Singlegame 부하 프로필
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
 * 시나리오별 threshold (docs/singlegame/load_performance_test.md 기준)
 * - save 쓰기 API SLA: p(95)<150ms
 * - rank 읽기 API SLA: p(95)<100ms
 * - analysis 읽기 API SLA: p(95)<200ms
 * - full-flow SLA: p(95)<300ms
 */
export const thresholds = {
  save: {
    http_req_duration: ['p(95)<150'],
    http_req_failed: ['rate<0.01'],
  },
  rank: {
    http_req_duration: ['p(95)<100'],
    http_req_failed: ['rate<0.01'],
  },
  analysis: {
    http_req_duration: ['p(95)<200'],
    http_req_failed: ['rate<0.01'],
  },
  'full-flow': {
    http_req_duration: ['p(95)<300'],
    http_req_failed: ['rate<0.01'],
  },
};

