package com.sparta.ecommerce;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Commit;
import org.springframework.test.context.ActiveProfiles;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * 부하 테스트용 고정 ID 데이터 생성기
 * k6 스크립트에서 사용할 예측 가능한 테스트 데이터 생성
 *
 * 실행 방법:
 * ./gradlew test --tests "LoadTestDataSeeder.seedForLoadTest"
 */
@SpringBootTest
@ActiveProfiles("local")
@Commit
public class LoadTestDataSeeder {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // 테스트 데이터 개수
    private static final int TEST_USER_COUNT = 100000;
    private static final int TEST_COUPON_QUANTITY = 100000;  // 쿠폰 재고
    private static final int TEST_PRODUCT_STOCK = 10000;     // 상품 재고

    @Test
    @DisplayName("부하 테스트용 데이터 생성 - k6 스크립트 전용")
    void seedForLoadTest() {
        System.out.println("=== 부하 테스트 데이터 생성 시작 ===");
        long startTime = System.currentTimeMillis();

        // 기존 테스트 데이터 삭제
        clearTestData();

        // 테스트 데이터 생성
        seedTestUsers();           // test-user-1 ~ test-user-100
        seedTestCoupon();          // test-coupon-1 (재고 10,000개)
        seedTestProducts();        // test-product-1 ~ test-product-10
        seedTestCarts();           // test-cart-1 ~ test-cart-100
        seedTestCartItems();       // 각 장바구니에 상품 1개씩

        long endTime = System.currentTimeMillis();
        System.out.println("=== 부하 테스트 데이터 생성 완료 ===");
        System.out.println("소요 시간: " + (endTime - startTime) / 1000.0 + "초");

        printTestDataSummary();
    }

    /**
     * 기존 테스트 데이터 삭제 (외래키 순서 고려)
     */
    private void clearTestData() {
        System.out.println("기존 테스트 데이터 삭제 중...");

        jdbcTemplate.update("DELETE FROM order_items WHERE order_id IN (SELECT id FROM orders WHERE user_id LIKE 'test-user-%')");
        jdbcTemplate.update("DELETE FROM payments WHERE order_id IN (SELECT id FROM orders WHERE user_id LIKE 'test-user-%')");
        jdbcTemplate.update("DELETE FROM orders WHERE user_id LIKE 'test-user-%'");
        jdbcTemplate.update("DELETE FROM cart_items WHERE cart_id IN (SELECT id FROM carts WHERE user_id LIKE 'test-user-%')");
        jdbcTemplate.update("DELETE FROM carts WHERE user_id LIKE 'test-user-%'");
        jdbcTemplate.update("DELETE FROM user_coupons WHERE user_id LIKE 'test-user-%'");
        jdbcTemplate.update("DELETE FROM users WHERE id LIKE 'test-user-%'");
        jdbcTemplate.update("DELETE FROM coupons WHERE id LIKE 'test-coupon-%'");
        jdbcTemplate.update("DELETE FROM products WHERE id LIKE 'test-product-%'");

        System.out.println("기존 테스트 데이터 삭제 완료");
    }

    /**
     * 테스트 유저 100명 생성
     * ID: test-user-1 ~ test-user-100
     * 잔액: 각 1,000,000원
     */
    private void seedTestUsers() {
        System.out.println("테스트 유저 생성 중...");

        String sql = "INSERT INTO users (id, email, name, balance, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)";

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                String userId = "test-user-" + (i + 1);

                ps.setString(1, userId);
                ps.setString(2, "testuser" + (i + 1) + "@loadtest.com");
                ps.setString(3, "테스트유저" + (i + 1));
                ps.setLong(4, 1_000_000);  // 100만원
                ps.setTimestamp(5, Timestamp.valueOf(LocalDateTime.now()));
                ps.setTimestamp(6, Timestamp.valueOf(LocalDateTime.now()));
            }

            @Override
            public int getBatchSize() {
                return TEST_USER_COUNT;
            }
        });

        System.out.printf("테스트 유저 생성 완료: %d명 (각 1,000,000원)%n", TEST_USER_COUNT);
    }

    /**
     * 테스트 쿠폰 생성
     * ID: test-coupon-1
     * 재고: 10,000개 (대량 부하 테스트용)
     */
    private void seedTestCoupon() {
        System.out.println("테스트 쿠폰 생성 중...");

        String sql = "INSERT INTO coupons (id, name, discount_type, discount_value, total_quantity, issued_quantity, remaining_quantity, min_order_amount, expires_at, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        jdbcTemplate.update(sql,
                "test-coupon-1",
                "부하테스트 쿠폰 (재고 10,000)",
                "FIXED",
                1000,
                TEST_COUPON_QUANTITY,    // 총 수량
                0,                        // 발급된 수량
                TEST_COUPON_QUANTITY,    // 남은 수량
                0,                        // 최소 주문 금액
                Timestamp.valueOf(LocalDateTime.now().plusDays(365)),
                Timestamp.valueOf(LocalDateTime.now()),
                Timestamp.valueOf(LocalDateTime.now())
        );

        System.out.printf("테스트 쿠폰 생성 완료: test-coupon-1 (재고 %d개)%n", TEST_COUPON_QUANTITY);
    }

    /**
     * 테스트 상품 10개 생성
     * ID: test-product-1 ~ test-product-10
     * 재고: 각 10,000개
     */
    private void seedTestProducts() {
        System.out.println("테스트 상품 생성 중...");

        String sql = "INSERT INTO products (id, name, description, price, stock, category, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                String productId = "test-product-" + (i + 1);

                ps.setString(1, productId);
                ps.setString(2, "부하테스트 상품 " + (i + 1));
                ps.setString(3, "부하테스트용 상품 (재고 " + TEST_PRODUCT_STOCK + "개)");
                ps.setLong(4, 10000);  // 10,000원
                ps.setInt(5, TEST_PRODUCT_STOCK);  // 재고 10,000개
                ps.setString(6, "테스트");
                ps.setTimestamp(7, Timestamp.valueOf(LocalDateTime.now()));
                ps.setTimestamp(8, Timestamp.valueOf(LocalDateTime.now()));
            }

            @Override
            public int getBatchSize() {
                return 10;
            }
        });

        System.out.printf("테스트 상품 생성 완료: 10개 (각 재고 %d개)%n", TEST_PRODUCT_STOCK);
    }

    /**
     * 테스트 장바구니 100개 생성
     * ID: test-cart-1 ~ test-cart-100
     */
    private void seedTestCarts() {
        System.out.println("테스트 장바구니 생성 중...");

        String sql = "INSERT INTO carts (id, user_id, created_at, updated_at) VALUES (?, ?, ?, ?)";

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                String cartId = "test-cart-" + (i + 1);
                String userId = "test-user-" + (i + 1);

                ps.setString(1, cartId);
                ps.setString(2, userId);
                ps.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
                ps.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()));
            }

            @Override
            public int getBatchSize() {
                return TEST_USER_COUNT;
            }
        });

        System.out.printf("테스트 장바구니 생성 완료: %d개%n", TEST_USER_COUNT);
    }

    /**
     * 테스트 장바구니 아이템 100개 생성
     * 각 장바구니에 test-product-1 상품 1개씩
     */
    private void seedTestCartItems() {
        System.out.println("테스트 장바구니 아이템 생성 중...");

        String sql = "INSERT INTO cart_items (cart_id, product_id, quantity, added_at) VALUES (?, ?, ?, ?)";

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                String cartId = "test-cart-" + (i + 1);

                ps.setString(1, cartId);
                ps.setString(2, "test-product-1");
                ps.setInt(3, 1);
                ps.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()));
            }

            @Override
            public int getBatchSize() {
                return TEST_USER_COUNT;
            }
        });

        System.out.printf("테스트 장바구니 아이템 생성 완료: %d개%n", TEST_USER_COUNT);
    }

    /**
     * 테스트 데이터 요약 출력
     */
    private void printTestDataSummary() {
        System.out.println("\n=== 부하 테스트 데이터 요약 ===");
        System.out.println("✅ 유저: test-user-1 ~ test-user-" + TEST_USER_COUNT + " (각 잔액 1,000,000원)");
        System.out.println("✅ 쿠폰: test-coupon-1 (재고 " + TEST_COUPON_QUANTITY + "개)");
        System.out.println("✅ 상품: test-product-1 ~ test-product-10 (각 재고 " + TEST_PRODUCT_STOCK + "개)");
        System.out.println("✅ 장바구니: test-cart-1 ~ test-cart-" + TEST_USER_COUNT);
        System.out.println("✅ 장바구니 아이템: 각 장바구니에 test-product-1 x 1개");

        System.out.println("\n=== k6 부하 테스트 시나리오 ===");
        System.out.println("1. 쿠폰 발급 테스트:");
        System.out.println("   k6 run k6-tests/coupon-issue-test.js");
        System.out.println("   - COUPON_ID: test-coupon-1");
        System.out.println("   - 사용자: test-user-1 ~ test-user-" + TEST_USER_COUNT);
        System.out.println();
        System.out.println("2. 주문/결제 테스트:");
        System.out.println("   k6 run k6-tests/order-payment-test.js");
        System.out.println("   - 사용자: test-user-1 ~ test-user-" + TEST_USER_COUNT);
        System.out.println("   - 장바구니에 상품 미리 담김");
        System.out.println();
        System.out.println("3. 잔액 충전 테스트:");
        System.out.println("   k6 run k6-tests/balance-charge-test.js");
        System.out.println("   - 사용자: test-user-1 ~ test-user-" + TEST_USER_COUNT);

        System.out.println("\n================================");
    }
}
