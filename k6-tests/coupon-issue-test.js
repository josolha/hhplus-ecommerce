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

// A) 상태 코드별 Counter
const status200Count = new Counter('status_200');
const status202Count = new Counter('status_202');
const status400Count = new Counter('status_400');
const status409Count = new Counter('status_409');
const status500Count = new Counter('status_500');
const statusOtherCount = new Counter('status_other');

// B) 비즈니스 결과별 Rate
const systemErrorRate = new Rate('system_error_rate');  // 5xx, 네트워크 오류
const bizSoldOutRate = new Rate('biz_soldout_rate');    // 품절 (정상 비즈니스 실패)
const bizDuplicateRate = new Rate('biz_duplicate_rate'); // 중복 차단 (정상 비즈니스 실패)
const successRate = new Rate('success_rate');            // 성공 (202/200)

// C) 결과별 Latency
const latencySuccess = new Trend('latency_success');
const latencySoldOut = new Trend('latency_soldout');
const latencyDuplicate = new Trend('latency_duplicate');
const latencySystemError = new Trend('latency_system_error');

// 레거시 호환용 (기존 메트릭 유지)
const errorRate = new Rate('errors');
const couponIssueLatency = new Trend('coupon_issue_latency');
const duplicateErrors = new Counter('duplicate_errors');
const soldOutErrors = new Counter('sold_out_errors');

// 테스트 설정
export const options = {
  scenarios: {
    // 시나리오 1: Sequential (고유 사용자 - 중복 없음, 순수 성능/정합성)
    sequential_test: {
      executor: 'per-vu-iterations',
      exec: 'sequentialTest',
      vus: 200,            // 200 VU
      iterations: 250,     // 각 VU가 250번씩 실행 (200 x 250 = 50,000 요청)
      maxDuration: '10m',  // 50,000건이면 10분이면 충분
    },

    // 시나리오 2: Load Test (랜덤 사용자 - 실제 사용 패턴, 재시도/중복 가능)
    load_test: {
      executor: 'ramping-vus',
      exec: 'loadTest',
      startVUs: 0,
      stages: [
        { duration: '1m', target: 100 },    // 1분간 100 VU로 증가
        { duration: '3m', target: 200 },    // 3분간 200 VU로 증가
        { duration: '2m', target: 200 },    // 2분간 200 VU 유지
        { duration: '30s', target: 0 },     // 30초간 0으로 감소
      ],
      startTime: '10m5s',  // sequential_test 종료 후 시작 (10분 + 5초)
    },

    // 시나리오 3: Peak Test (이벤트 폭주 상황 - 대량 동시 요청)
    peak_test: {
      executor: 'ramping-vus',
      exec: 'peakTest',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 300 },   // 30초간 300 VU로 급증
        { duration: '1m', target: 500 },    // 1분간 500 VU로 증가
        { duration: '2m', target: 500 },    // 2분간 500 VU 유지
        { duration: '30s', target: 0 },     // 30초간 0으로 감소
      ],
      startTime: '16m40s',  // load_test 종료 후 시작 (10m5s + 6m30s + 5초)
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
// Sequential이 1~50,000 사용하므로, 50,001~150,000 범위만 사용
function getRandomUserId() {
  const randomNum = Math.floor(Math.random() * 100000) + 50001;  // 50,001~150,000
  return `test-user-${randomNum}`;
}

// 순차 방식 (중복 없음, VU ID와 iteration 조합)
// VU 200개 × 각 250 iterations = 50,000명 고유 생성
function getSequentialUserId() {
  const vuId = exec.vu.idInTest;  // VU ID (1~200)
  const iter = exec.scenario.iterationInInstance;  // 해당 VU의 iteration (0~249)

  // VU 1, iter 0: test-user-1
  // VU 1, iter 249: test-user-250
  // VU 2, iter 0: test-user-251
  // VU 200, iter 249: test-user-50000
  const uniqueId = (vuId - 1) * 250 + iter + 1;

  return `test-user-${uniqueId}`;
}

// Sequential Test 시나리오 (고유 사용자 - 중복 없음)
export function sequentialTest() {
  const userId = getSequentialUserId();  // 고유한 사용자 ID

  const payload = JSON.stringify({
    userId: userId
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
    tags: {
      scenario: 'sequential_test',
      user_type: 'sequential'  // 태그로 구분
    },
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

  // A) 상태 코드별 카운트
  recordStatusCode(response.status);

  // B) 비즈니스 결과 분류 및 C) Latency 기록
  classifyAndRecordMetrics(response, userId);

  // 레거시 메트릭 유지
  couponIssueLatency.add(response.timings.duration);

  // Sequential 테스트는 대기 없이 바로 실행
}

// Load Test 시나리오 (랜덤 사용자 - 중복 시도 발생 가능)
export function loadTest() {
  const userId = getRandomUserId();  // 랜덤 방식 (중복 시도 가능)

  const payload = JSON.stringify({
    userId: userId
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
    tags: {
      scenario: 'load_test',
      user_type: 'random'  // D) 태그 강화
    },
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

  // A) 상태 코드별 카운트
  recordStatusCode(response.status);

  // B) 비즈니스 결과 분류 및 C) Latency 기록
  classifyAndRecordMetrics(response, userId);

  // 레거시 메트릭 유지
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
    tags: {
      scenario: 'peak_test',
      user_type: 'random'  // D) 태그 강화
    },
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

  // A) 상태 코드별 카운트
  recordStatusCode(response.status);

  // B) 비즈니스 결과 분류 및 C) Latency 기록
  classifyAndRecordMetrics(response, userId);

  // 레거시 메트릭 유지
  couponIssueLatency.add(response.timings.duration);

  // Peak 상황에서는 대기 없이 연속 요청
  sleep(0.1);
}

// ========== 헬퍼 함수 ==========

/**
 * A) 상태 코드별 카운트 기록
 */
function recordStatusCode(statusCode) {
  switch (statusCode) {
    case 200:
      status200Count.add(1);
      break;
    case 202:
      status202Count.add(1);
      break;
    case 400:
      status400Count.add(1);
      break;
    case 409:
      status409Count.add(1);
      break;
    case 500:
      status500Count.add(1);
      break;
    default:
      statusOtherCount.add(1);
  }
}

/**
 * B) 비즈니스 결과 분류 및 C) Latency 기록
 */
function classifyAndRecordMetrics(response, userId) {
  const status = response.status;
  const duration = response.timings.duration;
  const body = response.body || '';

  // 성공 케이스 (202 or 200)
  if (status === 202 || status === 200) {
    successRate.add(1);
    bizSoldOutRate.add(0);
    bizDuplicateRate.add(0);
    systemErrorRate.add(0);

    latencySuccess.add(duration);
    errorRate.add(0);

    return;
  }

  // 실패 케이스
  errorRate.add(1);
  successRate.add(0);

  // 409 (Conflict) - 중복 발급 (에러 코드 C006)
  if (status === 409) {
    bizDuplicateRate.add(1);
    bizSoldOutRate.add(0);
    systemErrorRate.add(0);

    latencyDuplicate.add(duration);
    duplicateErrors.add(1);

    console.log(`[중복 발급] userId: ${userId}`);
    return;
  }

  // 400 (Bad Request) - 품절 (에러 코드 C001)
  if (status === 400) {
    bizSoldOutRate.add(1);
    bizDuplicateRate.add(0);
    systemErrorRate.add(0);

    latencySoldOut.add(duration);
    soldOutErrors.add(1);

    console.log(`[품절] couponId: ${COUPON_ID}, body: ${body.substring(0, 50)}`);
    return;
  }

  // 시스템 에러 (5xx, 네트워크 오류, 기타)
  if (status >= 500 || status === 0) {
    systemErrorRate.add(1);
    bizSoldOutRate.add(0);
    bizDuplicateRate.add(0);

    latencySystemError.add(duration);

    console.log(`[시스템 에러] userId: ${userId}, status: ${status}`);
    return;
  }

  // 알 수 없는 400 에러
  systemErrorRate.add(1);
  bizSoldOutRate.add(0);
  bizDuplicateRate.add(0);

  latencySystemError.add(duration);

  console.log(`[알 수 없는 에러] userId: ${userId}, status: ${status}, body: ${body.substring(0, 150)}`);
}

// 테스트 시작 시 실행
export function setup() {
  console.log(`=== 쿠폰 발급 부하 테스트 시작 ===`);
  console.log(`Base URL: ${BASE_URL}`);
  console.log(`Coupon ID: ${COUPON_ID}`);
  console.log(`쿠폰 재고: 100,000개`);
  console.log(``);
  console.log(`[시나리오 구성]`);
  console.log(`시나리오           VU      목표 요청 수    특징`);
  console.log(`------------------------------------------------------------`);
  console.log(`Sequential        200        50,000     고유ID (중복 0, 정합성 검증)`);
  console.log(`Load Test      100~200    100~200K     랜덤 (sleep 1~3s, 실사용)`);
  console.log(`Peak Test      300~500    200~300K     랜덤 (sleep 0.1s, 폭주)`);
  console.log(`------------------------------------------------------------`);
  console.log(`Sequential: 50,000개 쿠폰 발급 (절반)`);
  console.log(`Load/Peak: 나머지 50,000개 경쟁 (중복/품절 발생)`);
  console.log(`예상 총 요청: 350,000~550,000건`);
  console.log(`총 소요 시간: 약 20분`);
  console.log(`====================================`);
}

// 테스트 종료 시 실행
export function teardown(data) {
  console.log(`=== 쿠폰 발급 부하 테스트 종료 ===`);
}

// E) 결과 요약 출력 (커스텀 메트릭 포함)
export function handleSummary(data) {
  return {
    'stdout': textSummary(data, { indent: ' ', enableColors: true }),
    'summary.json': JSON.stringify(data, null, 2),
  };
}

function textSummary(data, options) {
  const indent = options.indent || '';

  // 기본 메트릭
  const httpReqs = data.metrics.http_reqs?.values?.count || 0;
  const tps = data.metrics.http_reqs?.values?.rate || 0;

  // A) 상태 코드별 카운트
  const status200 = data.metrics.status_200?.values?.count || 0;
  const status202 = data.metrics.status_202?.values?.count || 0;
  const status400 = data.metrics.status_400?.values?.count || 0;
  const status409 = data.metrics.status_409?.values?.count || 0;
  const status500 = data.metrics.status_500?.values?.count || 0;
  const statusOther = data.metrics.status_other?.values?.count || 0;

  // B) 비즈니스 결과별 Rate
  const successRate = (data.metrics.success_rate?.values?.rate || 0) * 100;
  const bizDuplicateRate = (data.metrics.biz_duplicate_rate?.values?.rate || 0) * 100;
  const bizSoldOutRate = (data.metrics.biz_soldout_rate?.values?.rate || 0) * 100;
  const systemErrorRate = (data.metrics.system_error_rate?.values?.rate || 0) * 100;

  // C) 결과별 Latency (커스텀 메트릭 사용!)
  const latencySuccess = data.metrics.latency_success?.values || {};
  const latencySoldOut = data.metrics.latency_soldout?.values || {};
  const latencyDuplicate = data.metrics.latency_duplicate?.values || {};
  const latencySystemError = data.metrics.latency_system_error?.values || {};

  // 전체 Latency (비교용)
  const overallLatency = data.metrics.coupon_issue_latency?.values || {};

  // 레거시 메트릭
  const duplicateErrors = data.metrics.duplicate_errors?.values?.count || 0;
  const soldOutErrors = data.metrics.sold_out_errors?.values?.count || 0;
  const errorRate = (data.metrics.errors?.values?.rate || 0) * 100;

  return `
=== 쿠폰 발급 부하 테스트 결과 ===

[기본 통계]
총 요청 수: ${httpReqs.toLocaleString()}
평균 TPS: ${tps.toFixed(2)}

------------------------------------------------------------

[A) 상태 코드별 분포]
200 (OK):        ${status200.toLocaleString().padStart(8)} (${((status200/httpReqs)*100).toFixed(2)}%)
202 (Accepted):  ${status202.toLocaleString().padStart(8)} (${((status202/httpReqs)*100).toFixed(2)}%)
400 (Bad Req):   ${status400.toLocaleString().padStart(8)} (${((status400/httpReqs)*100).toFixed(2)}%)
409 (Conflict):  ${status409.toLocaleString().padStart(8)} (${((status409/httpReqs)*100).toFixed(2)}%)
500 (Error):     ${status500.toLocaleString().padStart(8)} (${((status500/httpReqs)*100).toFixed(2)}%)
Other:           ${statusOther.toLocaleString().padStart(8)} (${((statusOther/httpReqs)*100).toFixed(2)}%)

------------------------------------------------------------

[B) 비즈니스 결과 분류]
성공 (쿠폰 발급):      ${successRate.toFixed(2)}%
품절 (정상 차단):      ${bizSoldOutRate.toFixed(2)}%
중복 발급 (정상 차단): ${bizDuplicateRate.toFixed(2)}%
시스템 에러:           ${systemErrorRate.toFixed(2)}%

------------------------------------------------------------

[C) 결과별 응답 시간 (Latency)]

성공 케이스:
  평균: ${(latencySuccess.avg || 0).toFixed(2)}ms | p50: ${(latencySuccess.med || 0).toFixed(2)}ms | p95: ${(latencySuccess['p(95)'] || 0).toFixed(2)}ms | p99: ${(latencySuccess['p(99)'] || 0).toFixed(2)}ms

품절 케이스:
  평균: ${(latencySoldOut.avg || 0).toFixed(2)}ms | p50: ${(latencySoldOut.med || 0).toFixed(2)}ms | p95: ${(latencySoldOut['p(95)'] || 0).toFixed(2)}ms | p99: ${(latencySoldOut['p(99)'] || 0).toFixed(2)}ms

중복 발급 케이스:
  평균: ${(latencyDuplicate.avg || 0).toFixed(2)}ms | p50: ${(latencyDuplicate.med || 0).toFixed(2)}ms | p95: ${(latencyDuplicate['p(95)'] || 0).toFixed(2)}ms | p99: ${(latencyDuplicate['p(99)'] || 0).toFixed(2)}ms

시스템 에러 케이스:
  평균: ${(latencySystemError.avg || 0).toFixed(2)}ms | p50: ${(latencySystemError.med || 0).toFixed(2)}ms | p95: ${(latencySystemError['p(95)'] || 0).toFixed(2)}ms | p99: ${(latencySystemError['p(99)'] || 0).toFixed(2)}ms

전체 평균 (비교용):
  평균: ${(overallLatency.avg || 0).toFixed(2)}ms | p50: ${(overallLatency.med || 0).toFixed(2)}ms | p95: ${(overallLatency['p(95)'] || 0).toFixed(2)}ms | p99: ${(overallLatency['p(99)'] || 0).toFixed(2)}ms

------------------------------------------------------------

[상세 카운트]
전체 에러율: ${errorRate.toFixed(2)}%
중복 발급 차단: ${duplicateErrors.toLocaleString()}건
품절 차단: ${soldOutErrors.toLocaleString()}건

------------------------------------------------------------

[D) 시나리오별 비교]

Sequential Test (고유 사용자):
  - 중복 발급 에러: ${duplicateErrors.toLocaleString()}건
  - 기대: 중복 에러 0건 (모든 사용자가 고유함)
  - 실제 중복 발생 시 → 시스템 버그 의심

Random/Peak Test (랜덤 사용자):
  - 중복 발급 에러: 발생 가능 (정상)
  - 같은 사용자가 재요청하는 시나리오 재현

------------------------------------------------------------

[분석 포인트]
1. Sequential: 중복 에러가 0건이어야 정상 (고유 ID 보장)
2. Random/Peak: 중복 에러 발생 가능 (실제 사용자 행동 재현)
3. 성공 vs 품절 응답 시간 차이 (Redis 캐시 효과)
4. 시스템 에러가 0%에 가까우면 정상 (5xx, 타임아웃 없음)
5. 품절/중복은 정상적인 비즈니스 차단이므로 에러가 아님

============================================================
  `;
}
