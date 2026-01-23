/**
 * k6 부하 테스트 스크립트: 쿠폰 발급 API - Sequential Test (고유 사용자)
 *
 * 테스트 목적:
 * - 순수 성능 및 정합성 검증
 * - 중복 발급 에러 0건 기대 (모든 사용자가 고유함)
 * - Redis 재고 관리 정확성 검증
 *
 * 실행 방법:
 * k6 run coupon-issue-sequential-test.js
 */

import http from 'k6/http';
import { check } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';
import exec from 'k6/execution';

// 커스텀 메트릭 정의
const status200Count = new Counter('status_200');
const status202Count = new Counter('status_202');
const status400Count = new Counter('status_400');
const status409Count = new Counter('status_409');
const status500Count = new Counter('status_500');
const statusOtherCount = new Counter('status_other');

const systemErrorRate = new Rate('system_error_rate');
const bizSoldOutRate = new Rate('biz_soldout_rate');
const bizDuplicateRate = new Rate('biz_duplicate_rate');
const successRate = new Rate('success_rate');

const latencySuccess = new Trend('latency_success');
const latencySoldOut = new Trend('latency_soldout');
const latencyDuplicate = new Trend('latency_duplicate');
const latencySystemError = new Trend('latency_system_error');

const errorRate = new Rate('errors');
const couponIssueLatency = new Trend('coupon_issue_latency');
const duplicateErrors = new Counter('duplicate_errors');
const soldOutErrors = new Counter('sold_out_errors');

// 테스트 설정
export const options = {
  scenarios: {
    sequential_test: {
      executor: 'per-vu-iterations',
      exec: 'sequentialTest',
      vus: 200,            // 200 VU
      iterations: 250,     // 각 VU가 250번씩 실행 (200 x 250 = 50,000 요청)
      maxDuration: '10m',
    },
  },

  thresholds: {
    http_req_duration: ['p(95)<1000', 'p(99)<2000'],
    http_req_failed: ['rate<0.05'],
    errors: ['rate<0.01'],  // Sequential은 중복 에러가 거의 없어야 함
    biz_duplicate_rate: ['rate<0.01'],  // 중복 발급 1% 미만
  },
};

// 환경 설정
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';
const COUPON_ID = __ENV.COUPON_ID || 'test-coupon-1';

// 순차 방식 (중복 없음, VU ID와 iteration 조합)
// VU 200개 × 각 250 iterations = 50,000명 고유 생성
function getSequentialUserId() {
  const vuId = exec.vu.idInTest;                 // 1..200
  const iter = exec.vu.iterationInScenario;      // VU별 0..249 (per-vu-iterations에서 딱 맞음)

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
      user_type: 'sequential'
    },
  };

  const response = http.post(
    `${BASE_URL}/api/coupons/${COUPON_ID}/issue`,
    payload,
    params
  );

  // 응답 검증
  check(response, {
    'status is 202 or 200': (r) => r.status === 202 || r.status === 200,
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

// ========== 헬퍼 함수 ==========

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

  // 409 (Conflict) - 중복 발급
  if (status === 409) {
    bizDuplicateRate.add(1);
    bizSoldOutRate.add(0);
    systemErrorRate.add(0);

    latencyDuplicate.add(duration);
    duplicateErrors.add(1);

    console.log(`[⚠️ 중복 발급 - Sequential에서 발생하면 안 됨!] userId: ${userId}`);
    return;
  }

  // 400 (Bad Request) - 품절
  if (status === 400) {
    bizSoldOutRate.add(1);
    bizDuplicateRate.add(0);
    systemErrorRate.add(0);

    latencySoldOut.add(duration);
    soldOutErrors.add(1);

    console.log(`[품절] couponId: ${COUPON_ID}`);
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
  console.log(`=== 쿠폰 발급 Sequential Test 시작 ===`);
  console.log(`Base URL: ${BASE_URL}`);
  console.log(`Coupon ID: ${COUPON_ID}`);
  console.log(`VU: 200, Iterations: 250 per VU`);
  console.log(`총 예상 요청: 50,000건 (고유 사용자)`);
  console.log(`특징: 중복 에러 0건 기대, 순수 성능 검증`);
  console.log(`====================================`);
}

// 테스트 종료 시 실행
export function teardown(data) {
  console.log(`=== Sequential Test 종료 ===`);
}

// 결과 요약 출력
export function handleSummary(data) {
  const httpReqs = data.metrics.http_reqs?.values?.count || 0;
  const tps = data.metrics.http_reqs?.values?.rate || 0;

  const status200 = data.metrics.status_200?.values?.count || 0;
  const status202 = data.metrics.status_202?.values?.count || 0;
  const status400 = data.metrics.status_400?.values?.count || 0;
  const status409 = data.metrics.status_409?.values?.count || 0;
  const status500 = data.metrics.status_500?.values?.count || 0;
  const statusOther = data.metrics.status_other?.values?.count || 0;

  const successRate = (data.metrics.success_rate?.values?.rate || 0) * 100;
  const bizDuplicateRate = (data.metrics.biz_duplicate_rate?.values?.rate || 0) * 100;
  const bizSoldOutRate = (data.metrics.biz_soldout_rate?.values?.rate || 0) * 100;
  const systemErrorRate = (data.metrics.system_error_rate?.values?.rate || 0) * 100;

  const latencySuccess = data.metrics.latency_success?.values || {};
  const latencySoldOut = data.metrics.latency_soldout?.values || {};
  const latencyDuplicate = data.metrics.latency_duplicate?.values || {};

  const duplicateErrors = data.metrics.duplicate_errors?.values?.count || 0;
  const soldOutErrors = data.metrics.sold_out_errors?.values?.count || 0;

  const summary = `
=== Sequential Test 결과 ===

[기본 통계]
총 요청 수: ${httpReqs.toLocaleString()}
평균 TPS: ${tps.toFixed(2)}

------------------------------------------------------------

[상태 코드별 분포]
200 (OK):        ${status200.toLocaleString().padStart(8)} (${((status200/httpReqs)*100).toFixed(2)}%)
202 (Accepted):  ${status202.toLocaleString().padStart(8)} (${((status202/httpReqs)*100).toFixed(2)}%)
400 (Bad Req):   ${status400.toLocaleString().padStart(8)} (${((status400/httpReqs)*100).toFixed(2)}%)
409 (Conflict):  ${status409.toLocaleString().padStart(8)} (${((status409/httpReqs)*100).toFixed(2)}%) ⚠️
500 (Error):     ${status500.toLocaleString().padStart(8)} (${((status500/httpReqs)*100).toFixed(2)}%)

------------------------------------------------------------

[비즈니스 결과]
성공 (쿠폰 발급):      ${successRate.toFixed(2)}%
품절 (정상 차단):      ${bizSoldOutRate.toFixed(2)}%
중복 발급 (비정상!!):  ${bizDuplicateRate.toFixed(2)}% ⚠️ (0%여야 정상)
시스템 에러:           ${systemErrorRate.toFixed(2)}%

------------------------------------------------------------

[응답 시간]
성공: 평균 ${(latencySuccess.avg || 0).toFixed(2)}ms | p95 ${(latencySuccess['p(95)'] || 0).toFixed(2)}ms
품절: 평균 ${(latencySoldOut.avg || 0).toFixed(2)}ms | p95 ${(latencySoldOut['p(95)'] || 0).toFixed(2)}ms
중복: 평균 ${(latencyDuplicate.avg || 0).toFixed(2)}ms | p95 ${(latencyDuplicate['p(95)'] || 0).toFixed(2)}ms

------------------------------------------------------------

[검증 포인트]
✅ 중복 에러: ${duplicateErrors.toLocaleString()}건 (0건이어야 정상!)
✅ 품절 차단: ${soldOutErrors.toLocaleString()}건
✅ 성공률: ${successRate.toFixed(2)}%

${duplicateErrors > 0 ? '⚠️⚠️⚠️ 경고: Sequential Test에서 중복 에러 발생! 시스템 버그 의심 ⚠️⚠️⚠️' : '✅ 정상: 중복 에러 0건'}

============================================================
  `;

  console.log(summary);

  return {
    'stdout': summary,
    'summary-sequential.json': JSON.stringify(data, null, 2),
  };
}
