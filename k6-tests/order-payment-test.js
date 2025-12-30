/**
 * k6 부하 테스트 스크립트: 주문/결제 API
 *
 * 테스트 목적:
 * - 재고 차감 시 동시성 제어 검증 (SELECT FOR UPDATE)
 * - 잔액 차감 시 Pessimistic Lock 검증
 * - 트랜잭션 범위 및 DB Connection Pool 성능 측정
 * - 쿠폰 적용 시 할인 로직 검증
 *
 * 실행 방법:
 * k6 run order-payment-test.js
 *
 * 환경 변수 설정:
 * k6 run --env BASE_URL=http://localhost:8080 order-payment-test.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';
import exec from 'k6/execution';

// 커스텀 메트릭 정의
const errorRate = new Rate('errors');
const orderLatency = new Trend('order_latency');
const insufficientBalanceErrors = new Counter('insufficient_balance_errors');
const insufficientStockErrors = new Counter('insufficient_stock_errors');
const successfulOrders = new Counter('successful_orders');

// 테스트 설정
export const options = {
  scenarios: {
    // 시나리오 1: Load Test (실제 트래픽 시뮬레이션)
    load_test: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 50 },   // 30초 동안 50명까지 증가
        { duration: '1m', target: 100 },   // 1분 동안 100명 유지
        { duration: '30s', target: 0 },    // 30초 동안 0명으로 감소
      ],
      gracefulRampDown: '10s',
    },
  },

  thresholds: {
    http_req_duration: ['p(95)<2000', 'p(99)<3000'],  // 95%는 2초, 99%는 3초 이내
    errors: ['rate<0.15'],  // 에러율 15% 미만 (재고 부족 에러 포함)
    http_req_failed: ['rate<0.15'],
  },
};

// 환경 설정
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';

// 테스트 사용자 ID 생성 (DataSeeder로 생성된 test-user-1 ~ test-user-100000)
// 주문 후 장바구니가 비워지므로, 넓은 범위의 유저를 사용
function getRandomUserId() {
  const randomNum = Math.floor(Math.random() * 100000) + 1;
  return `test-user-${randomNum}`;
}

// 랜덤 쿠폰 ID 생성 (10% 확률로 쿠폰 사용, test-user-1~10만 쿠폰 보유)
function getRandomCouponId(userId) {
  // test-user-1 ~ test-user-10만 쿠폰이 있음
  const userNum = parseInt(userId.split('-')[2]);
  if (userNum <= 10 && Math.random() < 0.5) {
    return 'test-coupon-1';
  }
  return null;
}

// 테스트 시나리오
export default function () {
  const userId = getRandomUserId();
  const couponId = getRandomCouponId(userId);

  // STEP 1: 장바구니에 상품 추가 (선행 작업으로 가정)
  // 실제로는 장바구니에 미리 상품이 담겨있다고 가정

  // STEP 2: 주문 생성 및 결제
  const orderPayload = JSON.stringify({
    userId: userId,
    couponId: couponId
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
  };

  const orderResponse = http.post(
    `${BASE_URL}/api/orders`,
    orderPayload,
    params
  );

  // 응답 검증
  const success = check(orderResponse, {
    'order status is 200': (r) => r.status === 200,
    'order has orderId': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.orderId !== undefined;
      } catch (e) {
        return false;
      }
    },
    'response time < 5s': (r) => r.timings.duration < 5000,
  });

  // 에러 분류 및 기록
  if (success) {
    successfulOrders.add(1);
    errorRate.add(0);

    // 주문 성공 시 상세 정보 로깅
    try {
      const orderData = JSON.parse(orderResponse.body);
      console.log(`[주문 성공] userId: ${userId}, orderId: ${orderData.orderId}, finalAmount: ${orderData.finalAmount || 'N/A'}`);
    } catch (e) {
      // JSON 파싱 실패 시 무시
    }
  } else {
    errorRate.add(1);

    // 에러 유형 분류
    if (orderResponse.status === 400) {
      const body = orderResponse.body;

      if (body.includes('insufficient') || body.includes('부족') || body.includes('balance')) {
        insufficientBalanceErrors.add(1);
        console.log(`[잔액 부족] userId: ${userId}`);
      } else if (body.includes('stock') || body.includes('재고')) {
        insufficientStockErrors.add(1);
        console.log(`[재고 부족] userId: ${userId}`);
      }
    } else if (orderResponse.status === 500) {
      console.log(`[서버 에러] userId: ${userId}, status: 500`);
    } else if (orderResponse.status === 0) {
      console.log(`[연결 실패] userId: ${userId}, Connection timeout or refused`);
    }
  }

  // 응답 시간 기록
  orderLatency.add(orderResponse.timings.duration);

  // 사용자 행동 시뮬레이션 (부하 테스트용으로 대기 시간 제거)
  // sleep(0.1);

  // STEP 3: 주문 상세 조회 (10% 확률)
  if (success && Math.random() < 0.1) {
    try {
      const orderData = JSON.parse(orderResponse.body);
      const orderId = orderData.orderId;

      const detailResponse = http.get(
        `${BASE_URL}/api/orders/${orderId}`,
        params
      );

      check(detailResponse, {
        'order detail status is 200': (r) => r.status === 200,
      });
    } catch (e) {
      // 무시
    }

    // sleep(0.1);
  }
}

// 테스트 시작 시 실행
export function setup() {
  console.log(`=== 주문/결제 부하 테스트 시작 ===`);
  console.log(`Base URL: ${BASE_URL}`);
  console.log(`==============================`);

  // 사전 조건 확인 (선택 사항)
  // 1. 충분한 재고가 있는지
  // 2. 테스트 유저들의 잔액이 충전되어 있는지
}

// 테스트 종료 시 실행
export function teardown(data) {
  console.log(`=== 주문/결제 부하 테스트 종료 ===`);
}

// 결과 요약 출력
export function handleSummary(data) {
  const summary = generateTextSummary(data);

  return {
    'stdout': summary,
    'order-payment-summary.json': JSON.stringify(data, null, 2),
  };
}

function generateTextSummary(data) {
  const totalRequests = data.metrics.http_reqs?.values.count || 0;
  const avgTPS = data.metrics.http_reqs?.values.rate || 0;
  const successCount = data.metrics.successful_orders?.values.count || 0;
  const successRate = totalRequests > 0 ? ((successCount / totalRequests) * 100) : 0;

  return `
=== 주문/결제 부하 테스트 결과 ===

총 요청 수: ${totalRequests}
평균 TPS: ${avgTPS.toFixed(2)}
성공한 주문: ${successCount}
성공률: ${successRate.toFixed(2)}%

응답 시간:
  - 평균: ${(data.metrics.http_req_duration?.values.avg || 0).toFixed(2)}ms
  - 최소: ${(data.metrics.http_req_duration?.values.min || 0).toFixed(2)}ms
  - 최대: ${(data.metrics.http_req_duration?.values.max || 0).toFixed(2)}ms
  - p(50): ${(data.metrics['http_req_duration{p(50)']?.values || data.metrics.http_req_duration?.values['p(50)'] || 0).toFixed(2)}ms
  - p(95): ${(data.metrics['http_req_duration{p(95)']?.values || data.metrics.http_req_duration?.values['p(95)'] || 0).toFixed(2)}ms
  - p(99): ${(data.metrics['http_req_duration{p(99)']?.values || data.metrics.http_req_duration?.values['p(99)'] || 0).toFixed(2)}ms

에러 통계:
  - 전체 에러율: ${((data.metrics.errors?.values.rate || 0) * 100).toFixed(2)}%
  - 잔액 부족 에러: ${data.metrics.insufficient_balance_errors?.values.count || 0}
  - 재고 부족 에러: ${data.metrics.insufficient_stock_errors?.values.count || 0}

병목 분석 포인트:
  - p95 > 2000ms: DB Connection Pool 부족 또는 Lock 대기 시간 증가 의심
  - 에러율 > 15%: 재고 관리 로직 또는 동시성 제어 개선 필요
  - TPS < 50: 트랜잭션 범위 또는 Slow Query 분석 필요

=====================================
  `;
}
