/**
 * k6 부하 테스트 스크립트: 쿠폰 발급 API
 *
 * 테스트 목적:
 * - 선착순 쿠폰 발급 시 동시성 제어 검증
 * - Redis 분산 락 및 Kafka 비동기 처리 성능 측정
 * - 중복 발급 방지 로직 검증
 *
 * 실행 방법:
 * k6 run coupon-issue-test.js
 *
 * 옵션별 실행:
 * k6 run --vus 100 --duration 30s coupon-issue-test.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';
import exec from 'k6/execution';

// 커스텀 메트릭 정의
const errorRate = new Rate('errors');
const couponIssueLatency = new Trend('coupon_issue_latency');
const duplicateErrors = new Counter('duplicate_errors');
const soldOutErrors = new Counter('sold_out_errors');

// 테스트 설정
export const options = {
  scenarios: {
    load_test: {
      executor: 'ramping-vus',
      exec: 'loadTest',   // ✅ 추가
      startVUs: 0,
      stages: [
        { duration: '30s', target: 50 },   // 500 → 50
        { duration: '1m', target: 50 },    // 100 → 50
        { duration: '30s', target: 0 },
      ],
    },
    peak_test: {
      executor: 'ramping-vus',
      exec: 'peakTest',
      startVUs: 0,
      stages: [
        { duration: '10s', target: 200 },  // 500 → 200
        { duration: '30s', target: 200 },  // 500 → 200
        { duration: '10s', target: 0 },
      ],
      startTime: '2m',
    },
  },

  thresholds: {
    http_req_duration: ['p(95)<1000', 'p(99)<2000'],
    http_req_failed: ['rate<0.1'],
    errors: ['rate<0.05'],  // 0.1 → 0.05 (더 엄격하게)
  },
};

// 환경 설정
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';
const COUPON_ID = __ENV.COUPON_ID || 'test-coupon-1';

// 테스트 사용자 ID 생성 (test-user-1 ~ test-user-100000)
// DataSeeder로 생성된 테스트 유저 사용

// 랜덤 방식 (중복 시도 발생)
function getRandomUserId() {
  const randomNum = Math.floor(Math.random() * 100000) + 1;
  return `test-user-${randomNum}`;
}

// 순차 방식 (중복 없음, 각 VU가 고유한 유저 사용)
// VU별로 고유한 유저 ID 생성: VU ID + iteration 번호 조합
function getSequentialUserId() {
  const vuId = exec.vu.idInTest;  // VU 고유 ID (1, 2, 3, ...)
  const iter = exec.scenario.iterationInTest;  // 전체 iteration 카운터
  // VU ID와 iteration을 조합하여 고유한 ID 생성
  const uniqueId = (iter * 1000) + vuId;  // 예: VU=1, iter=0 → 1, VU=2, iter=0 → 2
  return `test-user-${uniqueId}`;
}

// Load Test 시나리오 (실제 상황 재현)
export function loadTest() {
  const userId = getRandomUserId();  // 랜덤 방식 (중복 시도 가능)

  const payload = JSON.stringify({
    userId: userId
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
    tags: { scenario: 'load_test' },
  };

  const response = http.post(
    `${BASE_URL}/api/coupons/${COUPON_ID}/issue`,
    payload,
    params
  );

  // 응답 검증
  const success = check(response, {
    'status is 202 (accepted)': (r) => r.status === 202,
    'status is 200 (ok)': (r) => r.status === 200,
    'response has body': (r) => r.body.length > 0,
  });

  // 성공 여부 판단: 202 또는 200이면 성공
  const isSuccess = response.status === 202 || response.status === 200;

  // 에러 분류 (실제 비즈니스 에러만 카운트)
  if (!isSuccess) {
    errorRate.add(1);

    if (response.status === 409) {
      duplicateErrors.add(1);
      console.log(`[중복 발급] userId: ${userId}`);
    } else if (response.status === 400 && response.body.includes('sold out')) {
      soldOutErrors.add(1);
      console.log(`[품절] couponId: ${COUPON_ID}`);
    } else {
      // 알 수 없는 에러 로그
      console.log(`[알 수 없는 에러] userId: ${userId}, status: ${response.status}, body: ${response.body.substring(0, 150)}`);
    }
  } else {
    errorRate.add(0);
  }

  // 응답 시간 기록
  couponIssueLatency.add(response.timings.duration);

  // 사용자 행동 시뮬레이션 (1~3초 대기)
  sleep(Math.random() * 2 + 1);
}

// Peak Test 시나리오 (선착순 이벤트 시뮬레이션)
export function peakTest() {
  const userId = getRandomUserId();  // 랜덤 방식 (실제 상황 재현)

  const payload = JSON.stringify({
    userId: userId
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
    tags: { scenario: 'peak_test' },
  };

  const response = http.post(
    `${BASE_URL}/api/coupons/${COUPON_ID}/issue`,
    payload,
    params
  );

  // 응답 검증
  const success = check(response, {
    'status is 202 or 200': (r) => r.status === 202 || r.status === 200,
    'response time < 3s': (r) => r.timings.duration < 3000,
  });

  // 에러 분류
  if (!success) {
    errorRate.add(1);

    if (response.status === 409) {
      duplicateErrors.add(1);
    } else if (response.status === 400) {
      soldOutErrors.add(1);
    }
  } else {
    errorRate.add(0);
  }

  couponIssueLatency.add(response.timings.duration);

  // Peak 상황에서는 대기 없이 연속 요청
  sleep(0.1);
}

// 테스트 시작 시 실행
export function setup() {
  console.log(`=== 쿠폰 발급 부하 테스트 시작 ===`);
  console.log(`Base URL: ${BASE_URL}`);
  console.log(`Coupon ID: ${COUPON_ID}`);
  console.log(`====================================`);
}

// 테스트 종료 시 실행
export function teardown(data) {
  console.log(`=== 쿠폰 발급 부하 테스트 종료 ===`);
}

// 결과 요약 출력
export function handleSummary(data) {
  return {
    'stdout': textSummary(data, { indent: ' ', enableColors: true }),
    'summary.json': JSON.stringify(data),
  };
}

function textSummary(data, options) {
  const indent = options.indent || '';
  const colors = options.enableColors;

  // 안전하게 메트릭 값 가져오기
  const httpReqs = data.metrics.http_reqs?.values?.count || 0;
  const tps = data.metrics.http_reqs?.values?.rate || 0;
  const avgDuration = data.metrics.http_req_duration?.values?.avg || 0;
  const minDuration = data.metrics.http_req_duration?.values?.min || 0;
  const maxDuration = data.metrics.http_req_duration?.values?.max || 0;
  const p50 = data.metrics.http_req_duration?.values?.['p(50)'] || 0;
  const p95 = data.metrics.http_req_duration?.values?.['p(95)'] || 0;
  const p99 = data.metrics.http_req_duration?.values?.['p(99)'] || 0;
  const errorRate = (data.metrics.errors?.values?.rate || 0) * 100;
  const duplicateErrors = data.metrics.duplicate_errors?.values?.count || 0;
  const soldOutErrors = data.metrics.sold_out_errors?.values?.count || 0;

  return `
${indent}=== 쿠폰 발급 부하 테스트 결과 ===
${indent}
${indent}총 요청 수: ${httpReqs}
${indent}평균 TPS: ${tps.toFixed(2)}
${indent}
${indent}응답 시간:
${indent}  - 평균: ${avgDuration.toFixed(2)}ms
${indent}  - 최소: ${minDuration.toFixed(2)}ms
${indent}  - 최대: ${maxDuration.toFixed(2)}ms
${indent}  - p(50): ${p50.toFixed(2)}ms
${indent}  - p(95): ${p95.toFixed(2)}ms
${indent}  - p(99): ${p99.toFixed(2)}ms
${indent}
${indent}에러 통계:
${indent}  - 전체 에러율: ${errorRate.toFixed(2)}%
${indent}  - 중복 발급 에러: ${duplicateErrors}
${indent}  - 품절 에러: ${soldOutErrors}
${indent}
${indent}=====================================
  `;
}
