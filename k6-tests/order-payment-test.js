/**
 * k6 부하 테스트 스크립트: 주문/결제 API (최종 안정판)
 *
 * 기준
 * - 성공: 200
 * - 비즈니스 실패: 400 (정상)
 * - 시스템 실패: 5xx, status=0 (실패)
 */

import http from "k6/http";
import { check, sleep } from "k6";
import { Rate, Trend, Counter } from "k6/metrics";

// ===== Metrics =====
const systemErrorRate = new Rate("system_error_rate"); // 오직 5xx / 0
const bizFailRate = new Rate("biz_fail_rate");         // 400
const successRate = new Rate("success_rate");          // 200

const latencySuccess = new Trend("latency_success");
const latencyBizFail = new Trend("latency_biz_fail");
const latencySystemFail = new Trend("latency_system_fail");

const okCount = new Counter("ok_count");
const bizFailCount = new Counter("biz_fail_count");
const systemFailCount = new Counter("system_fail_count");

const insufficientBalanceCount = new Counter("insufficient_balance_count");
const insufficientStockCount = new Counter("insufficient_stock_count");
const unknown400Count = new Counter("unknown_400_count");

// ===== Options =====
export const options = {
  scenarios: {
    load_test: {
      executor: "ramping-vus",
      startVUs: 0,
      stages: [
        { duration: "30s", target: 50 },
        { duration: "1m", target: 100 },
        { duration: "30s", target: 0 },
      ],
      gracefulRampDown: "10s",
    },
  },

  thresholds: {
    http_req_duration: ["p(95)<2000", "p(99)<3000"],

    // ❗ 실패 기준은 이것 하나뿐
    system_error_rate: ["rate<0.01"],
  },
};

// ===== Env =====
const BASE_URL = __ENV.BASE_URL || "http://localhost:8081";
const USER_POOL_SIZE = parseInt(__ENV.USER_POOL_SIZE || "150000", 10);

const COUPON_ENABLED = (__ENV.COUPON_ENABLED || "false").toLowerCase() === "true";
const COUPON_ID = __ENV.COUPON_ID || "test-coupon-1";
const COUPON_USER_MAX = parseInt(__ENV.COUPON_USER_MAX || "0", 10);

const SLEEP_MAX_MS = parseInt(__ENV.SLEEP_MAX_MS || "200", 10);

// ===== Helpers =====
function randInt(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

function getRandomUserId() {
  return `test-user-${randInt(1, USER_POOL_SIZE)}`;
}

function getCouponIdIfEnabled(userId) {
  if (!COUPON_ENABLED || COUPON_USER_MAX <= 0) return null;

  const userNum = parseInt(userId.split("-")[2], 10);
  if (userNum <= COUPON_USER_MAX && Math.random() < 0.1) {
    return COUPON_ID;
  }
  return null;
}

function classify400(body) {
  const b = (body || "").toLowerCase();
  if (b.includes("잔액") || b.includes("balance")) return "BALANCE";
  if (b.includes("재고") || b.includes("stock") || b.includes("품절")) return "STOCK";
  return "UNKNOWN";
}

// ===== Test =====
export default function () {
  const userId = getRandomUserId();
  const couponId = getCouponIdIfEnabled(userId);

  const res = http.post(
      `${BASE_URL}/api/orders`,
      JSON.stringify({ userId, couponId }),
      { headers: { "Content-Type": "application/json" } }
  );

  const d = res.timings.duration;

  // === SYSTEM FAIL ===
  if (res.status === 0 || res.status >= 500) {
    systemErrorRate.add(1);
    bizFailRate.add(0);
    successRate.add(0);

    systemFailCount.add(1);
    latencySystemFail.add(d);

    if (Math.random() < 0.01) {
      console.log(`[SYSTEM_FAIL] status=${res.status} userId=${userId}`);
    }

    sleep(Math.random() * (SLEEP_MAX_MS / 1000));
    return;
  }

  // === SUCCESS ===
  if (res.status === 200) {
    systemErrorRate.add(0);
    bizFailRate.add(0);
    successRate.add(1);

    okCount.add(1);
    latencySuccess.add(d);

    check(res, {
      "200 OK": (r) => r.status === 200,
    });

    sleep(Math.random() * (SLEEP_MAX_MS / 1000));
    return;
  }

  // === BIZ FAIL ===
  if (res.status === 400) {
    systemErrorRate.add(0);
    bizFailRate.add(1);
    successRate.add(0);

    bizFailCount.add(1);
    latencyBizFail.add(d);

    const kind = classify400(res.body);
    if (kind === "BALANCE") insufficientBalanceCount.add(1);
    else if (kind === "STOCK") insufficientStockCount.add(1);
    else unknown400Count.add(1);

    sleep(Math.random() * (SLEEP_MAX_MS / 1000));
    return;
  }

  // === UNEXPECTED ===
  systemErrorRate.add(1);
  systemFailCount.add(1);
  latencySystemFail.add(d);
}

// ===== Summary =====
export function handleSummary(data) {
  const total = data.metrics.http_reqs.values.count;

  return {
    stdout: `
=== 주문/결제 부하 테스트 결과 ===
총 요청: ${total}

성공(200): ${data.metrics.ok_count?.values.count || 0}
비즈니스 실패(400): ${data.metrics.biz_fail_count?.values.count || 0}
시스템 실패(5xx/0): ${data.metrics.system_fail_count?.values.count || 0}

시스템 에러율: ${(data.metrics.system_error_rate.values.rate * 100).toFixed(2)}%
================================
`,
  };
}