/**
 * k6 부하 테스트 스크립트: 쿠폰 발급 API - Random Test (실제 사용자 패턴)
 *
 * 목표(수정 포인트)
 * 1) 재고가 100,000일 때도 "품절(400)"이 충분히 나오도록
 *    → 랜덤 유저 풀을 재고보다 크게(예: 300,000) 잡아서 중복(409) 때문에 재고가 덜 소진되는 문제를 줄임
 * 2) 그 외 로직은 유지
 *
 * 실행:
 * k6 run coupon-issue-random-test.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

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

// 환경 설정
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';
const COUPON_ID = __ENV.COUPON_ID || 'test-coupon-1';

// ✅ 수정: 랜덤 유저 풀을 크게 잡기 (재고 100,000 기준 3배 권장)
const USER_POOL_SIZE = Number(__ENV.USER_POOL_SIZE || 300000); // 기본 300,000

// 테스트 설정
export const options = {
  scenarios: {
    // 시나리오 1: Load Test (랜덤 사용자 - 실제 사용 패턴)
    load_test: {
      executor: 'ramping-vus',
      exec: 'loadTest',
      startVUs: 0,
      stages: [
        { duration: '1m', target: 100 },
        { duration: '3m', target: 200 },
        { duration: '2m', target: 200 },
        { duration: '30s', target: 0 },
      ],
    },

    // 시나리오 2: Peak Test (이벤트 폭주 상황)
    peak_test: {
      executor: 'ramping-vus',
      exec: 'peakTest',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 300 },
        { duration: '1m', target: 500 },
        { duration: '2m', target: 500 },
        { duration: '30s', target: 0 },
      ],
      startTime: '7m',
    },
  },

  thresholds: {
    http_req_duration: ['p(95)<1000', 'p(99)<2000'],
    http_req_failed: ['rate<0.1'],
    system_error_rate: ['rate<0.01'],  // 시스템 에러만 1% 미만 (중복/품절은 정상)
  },
};

// 랜덤 방식 (중복 시도 발생)
function getRandomUserId() {
  const randomNum = Math.floor(Math.random() * USER_POOL_SIZE) + 1; // ✅ 1~USER_POOL_SIZE
  return `test-user-${randomNum}`;
}

// Load Test 시나리오 (랜덤 사용자 - 실제 사용 패턴)
export function loadTest() {
  const userId = getRandomUserId();

  const payload = JSON.stringify({ userId });
  const params = {
    headers: { 'Content-Type': 'application/json' },
    tags: { scenario: 'load_test', user_type: 'random' },
  };

  const response = http.post(`${BASE_URL}/api/coupons/${COUPON_ID}/issue`, payload, params);

  // 응답 검증 (202/200/400/409 다 정상 시나리오일 수 있으니, 여기서는 본문만 체크)
  check(response, {
    'response has body': (r) => (r.body || '').length > 0,
  });

  recordStatusCode(response.status);
  classifyAndRecordMetrics(response, userId);
  couponIssueLatency.add(response.timings.duration);

  // 사용자 행동 시뮬레이션 (1~3초 대기)
  sleep(Math.random() * 2 + 1);
}

// Peak Test 시나리오 (이벤트 폭주)
export function peakTest() {
  const userId = getRandomUserId();

  const payload = JSON.stringify({ userId });
  const params = {
    headers: { 'Content-Type': 'application/json' },
    tags: { scenario: 'peak_test', user_type: 'random' },
  };

  const response = http.post(`${BASE_URL}/api/coupons/${COUPON_ID}/issue`, payload, params);

  check(response, {
    'response time < 3s': (r) => r.timings.duration < 3000,
    'response has body': (r) => (r.body || '').length > 0,
  });

  recordStatusCode(response.status);
  classifyAndRecordMetrics(response, userId);
  couponIssueLatency.add(response.timings.duration);

  // Peak 상황에서는 대기 없이 연속 요청
  sleep(0.1);
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

  // 409 - 중복 발급 (정상)
  if (status === 409) {
    bizDuplicateRate.add(1);
    bizSoldOutRate.add(0);
    systemErrorRate.add(0);

    latencyDuplicate.add(duration);
    duplicateErrors.add(1);
    return;
  }

  // 400 - 품절 (정상)
  if (status === 400) {
    bizSoldOutRate.add(1);
    bizDuplicateRate.add(0);
    systemErrorRate.add(0);

    latencySoldOut.add(duration);
    soldOutErrors.add(1);
    return;
  }

  // 시스템 에러
  if (status >= 500 || status === 0) {
    systemErrorRate.add(1);
    bizSoldOutRate.add(0);
    bizDuplicateRate.add(0);

    latencySystemError.add(duration);
    console.log(`[시스템 에러] userId: ${userId}, status: ${status}`);
    return;
  }

  // 기타 알 수 없는 에러
  systemErrorRate.add(1);
  bizSoldOutRate.add(0);
  bizDuplicateRate.add(0);

  latencySystemError.add(duration);
  console.log(`[알 수 없는 에러] userId: ${userId}, status: ${status}, body: ${body.substring(0, 150)}`);
}

// 테스트 시작 시 실행
export function setup() {
  console.log(`=== 쿠폰 발급 Random Test 시작 ===`);
  console.log(`Base URL: ${BASE_URL}`);
  console.log(`Coupon ID: ${COUPON_ID}`);
  console.log(`USER_POOL_SIZE: ${USER_POOL_SIZE.toLocaleString()} (랜덤 유저 풀)`);
  console.log(``);
  console.log(`[시나리오 구성]`);
  console.log(`1) Load Test (6.5분)`);
  console.log(`   - 100~200 VU, Sleep 1~3초`);
  console.log(`2) Peak Test (4분, Load 종료 후 시작)`);
  console.log(`   - 300~500 VU, Sleep 0.1초`);
  console.log(``);
  console.log(`의도: 유저 풀을 크게 잡아 중복(409) 비율을 낮추고, 재고 소진 후 품절(400)도 충분히 관측`);
  console.log(`====================================`);
}

export function teardown() {
  console.log(`=== Random Test 종료 ===`);
}

export function handleSummary(data) {
  const httpReqs = data.metrics.http_reqs?.values?.count || 0;
  const tps = data.metrics.http_reqs?.values?.rate || 0;

  const status200 = data.metrics.status_200?.values?.count || 0;
  const status202 = data.metrics.status_202?.values?.count || 0;
  const status400 = data.metrics.status_400?.values?.count || 0;
  const status409 = data.metrics.status_409?.values?.count || 0;
  const status500 = data.metrics.status_500?.values?.count || 0;

  const successPct = (data.metrics.success_rate?.values?.rate || 0) * 100;
  const dupPct = (data.metrics.biz_duplicate_rate?.values?.rate || 0) * 100;
  const soldOutPct = (data.metrics.biz_soldout_rate?.values?.rate || 0) * 100;
  const sysPct = (data.metrics.system_error_rate?.values?.rate || 0) * 100;

  const latencySuccessVals = data.metrics.latency_success?.values || {};
  const latencySoldOutVals = data.metrics.latency_soldout?.values || {};
  const latencyDupVals = data.metrics.latency_duplicate?.values || {};

  const dupCount = data.metrics.duplicate_errors?.values?.count || 0;
  const soldOutCount = data.metrics.sold_out_errors?.values?.count || 0;

  const summary = `
=== Random Test 결과 ===

[기본 통계]
총 요청 수: ${httpReqs.toLocaleString()}
평균 TPS: ${tps.toFixed(2)}
USER_POOL_SIZE: ${USER_POOL_SIZE.toLocaleString()}

------------------------------------------------------------

[상태 코드별 분포]
200 (OK):        ${status200.toLocaleString().padStart(8)} (${httpReqs ? ((status200/httpReqs)*100).toFixed(2) : '0.00'}%)
202 (Accepted):  ${status202.toLocaleString().padStart(8)} (${httpReqs ? ((status202/httpReqs)*100).toFixed(2) : '0.00'}%)
400 (SoldOut):   ${status400.toLocaleString().padStart(8)} (${httpReqs ? ((status400/httpReqs)*100).toFixed(2) : '0.00'}%)
409 (Duplicate): ${status409.toLocaleString().padStart(8)} (${httpReqs ? ((status409/httpReqs)*100).toFixed(2) : '0.00'}%)
500 (Error):     ${status500.toLocaleString().padStart(8)} (${httpReqs ? ((status500/httpReqs)*100).toFixed(2) : '0.00'}%)

------------------------------------------------------------

[비즈니스 결과]
성공(202/200):   ${successPct.toFixed(2)}%
품절(400):       ${soldOutPct.toFixed(2)}%
중복(409):       ${dupPct.toFixed(2)}%
시스템 에러:     ${sysPct.toFixed(2)}%

------------------------------------------------------------

[응답 시간]
성공: 평균 ${(latencySuccessVals.avg || 0).toFixed(2)}ms | p95 ${(latencySuccessVals['p(95)'] || 0).toFixed(2)}ms
품절: 평균 ${(latencySoldOutVals.avg || 0).toFixed(2)}ms | p95 ${(latencySoldOutVals['p(95)'] || 0).toFixed(2)}ms
중복: 평균 ${(latencyDupVals.avg || 0).toFixed(2)}ms | p95 ${(latencyDupVals['p(95)'] || 0).toFixed(2)}ms

------------------------------------------------------------

[카운트]
중복(409) 카운트: ${dupCount.toLocaleString()}
품절(400) 카운트: ${soldOutCount.toLocaleString()}

============================================================
  `;

  console.log(summary);

  return {
    stdout: summary,
    'summary-random.json': JSON.stringify(data, null, 2),
  };
}