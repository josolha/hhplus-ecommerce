/**
 * k6 부하 테스트 스크립트: 잔액 충전 API
 *
 * 테스트 목적:
 * - 잔액 충전 시 동시성 제어 검증 (Pessimistic Lock)
 * - 동일 사용자의 동시 충전 요청 처리 검증
 * - Balance 업데이트 트랜잭션 성능 측정
 *
 * 실행 방법:
 * k6 run balance-charge-test.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// 커스텀 메트릭 정의
const errorRate = new Rate('errors');
const chargeLatency = new Trend('charge_latency');
const successfulCharges = new Counter('successful_charges');
const concurrentConflicts = new Counter('concurrent_conflicts');

// 테스트 설정
export const options = {
  scenarios: {
    // 시나리오 1: 동일 사용자 동시 충전 테스트
    concurrent_charge_test: {
      executor: 'constant-vus',
      vus: 100,
      duration: '1m',
      exec: 'concurrentChargeTest',
    },

    // 시나리오 2: 일반 충전 부하 테스트
    load_test: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 50 },
        { duration: '1m', target: 100 },
        { duration: '1m', target: 150 },
        { duration: '30s', target: 0 },
      ],
      startTime: '2m',  // concurrent_charge_test 종료 후 시작
      gracefulRampDown: '10s',
      exec: 'loadTest',
    },
  },

  thresholds: {
    http_req_duration: ['p(95)<1500', 'p(99)<2500'],
    errors: ['rate<0.05'],  // 에러율 5% 미만
    http_req_failed: ['rate<0.05'],
  },
};

// 환경 설정
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';

// 테스트 사용자 ID 생성 (DataSeeder로 생성된 test-user-1 ~ test-user-100)
function getRandomUserId() {
  const randomNum = Math.floor(Math.random() * 100) + 1;
  return `test-user-${randomNum}`;
}

// 동시성 충돌 테스트용 사용자 ID (10명만 사용하여 충돌 유도)
function getConflictUserId() {
  const randomNum = Math.floor(Math.random() * 10) + 1;
  return `test-user-${randomNum}`;
}

// 랜덤 충전 금액 생성 (1,000 ~ 100,000원)
function getRandomAmount() {
  return Math.floor(Math.random() * 99) * 1000 + 1000;
}

// 동시 충전 테스트 (동일 사용자에 대한 동시 요청)
export function concurrentChargeTest() {
  const userId = getConflictUserId();  // 소수의 사용자 풀 사용
  const amount = getRandomAmount();

  const payload = JSON.stringify({
    amount: amount
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
    tags: { scenario: 'concurrent_charge' },
  };

  const response = http.post(
    `${BASE_URL}/api/users/${userId}/balance/charge`,
    payload,
    params
  );

  // 응답 검증
  const success = check(response, {
    'charge status is 200': (r) => r.status === 200,
    'response has balance': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.currentBalance !== undefined;
      } catch (e) {
        return false;
      }
    },
    'response time < 3s': (r) => r.timings.duration < 3000,
  });

  if (success) {
    successfulCharges.add(1);
    errorRate.add(0);

    try {
      const data = JSON.parse(response.body);
      console.log(`[충전 성공] userId: ${userId}, amount: ${amount}, balance: ${data.currentBalance}`);
    } catch (e) {
      // 무시
    }
  } else {
    errorRate.add(1);

    // 동시성 충돌 감지
    if (response.status === 409 || response.status === 423) {
      concurrentConflicts.add(1);
      console.log(`[동시성 충돌] userId: ${userId}, status: ${response.status}`);
    } else if (response.status === 500) {
      console.log(`[서버 에러] userId: ${userId}`);
    } else if (response.status === 0) {
      console.log(`[연결 실패] userId: ${userId}`);
    }
  }

  chargeLatency.add(response.timings.duration);

  // 순수 성능 측정을 위해 sleep 제거
  // sleep(0.5);
}

// 일반 충전 부하 테스트
export function loadTest() {
  const userId = getRandomUserId();
  const amount = getRandomAmount();

  const payload = JSON.stringify({
    amount: amount
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
    tags: { scenario: 'load_test' },
  };

  // 충전 API 호출
  const chargeResponse = http.post(
    `${BASE_URL}/api/users/${userId}/balance/charge`,
    payload,
    params
  );

  const success = check(chargeResponse, {
    'charge status is 200': (r) => r.status === 200,
    'response time < 2s': (r) => r.timings.duration < 2000,
  });

  if (success) {
    successfulCharges.add(1);
    errorRate.add(0);
  } else {
    errorRate.add(1);
  }

  chargeLatency.add(chargeResponse.timings.duration);

  // 순수 성능 측정을 위해 sleep 제거
  // sleep(Math.random() * 2 + 1);

  // 잔액 조회 (30% 확률)
  if (Math.random() < 0.3) {
    const balanceResponse = http.get(
      `${BASE_URL}/api/users/${userId}/balance`,
      params
    );

    check(balanceResponse, {
      'balance status is 200': (r) => r.status === 200,
    });

    // 순수 성능 측정을 위해 sleep 제거
    // sleep(0.5);
  }
}

// 테스트 시작 시 실행
export function setup() {
  console.log(`=== 잔액 충전 부하 테스트 시작 ===`);
  console.log(`Base URL: ${BASE_URL}`);
  console.log(`================================`);
}

// 테스트 종료 시 실행
export function teardown(data) {
  console.log(`=== 잔액 충전 부하 테스트 종료 ===`);
}

// 결과 요약 출력
export function handleSummary(data) {
  const summary = generateTextSummary(data);

  return {
    'stdout': summary,
    'balance-charge-summary.json': JSON.stringify(data, null, 2),
  };
}

function generateTextSummary(data) {
  const totalRequests = data.metrics.http_reqs?.values.count || 0;
  const avgTPS = data.metrics.http_reqs?.values.rate || 0;
  const successCount = data.metrics.successful_charges?.values.count || 0;
  const conflictCount = data.metrics.concurrent_conflicts?.values.count || 0;

  return `
=== 잔액 충전 부하 테스트 결과 ===

총 요청 수: ${totalRequests}
평균 TPS: ${avgTPS.toFixed(2)}
성공한 충전: ${successCount}
동시성 충돌: ${conflictCount}

응답 시간:
  - 평균: ${(data.metrics.http_req_duration?.values.avg || 0).toFixed(2)}ms
  - 최소: ${(data.metrics.http_req_duration?.values.min || 0).toFixed(2)}ms
  - 최대: ${(data.metrics.http_req_duration?.values.max || 0).toFixed(2)}ms
  - p(50): ${(data.metrics.http_req_duration?.values.med || 0).toFixed(2)}ms
  - p(90): ${(data.metrics.http_req_duration?.values['p(90)'] || 0).toFixed(2)}ms
  - p(95): ${(data.metrics.http_req_duration?.values['p(95)'] || 0).toFixed(2)}ms

에러 통계:
  - 전체 에러율: ${((data.metrics.errors?.values.rate || 0) * 100).toFixed(2)}%
  - 동시성 충돌 (409/423): ${conflictCount}

동시성 제어 분석:
  - 동시성 충돌이 0에 가까우면: Lock이 정상 작동
  - 동시성 충돌이 많으면: Lock 타임아웃 설정 확인 필요
  - p95 > 1500ms: Lock 대기 시간 또는 DB Connection Pool 확인

병목 분석 포인트:
  - Pessimistic Lock으로 인한 대기 시간
  - DB Connection Pool 크기
  - 트랜잭션 처리 시간

===================================
  `;
}
