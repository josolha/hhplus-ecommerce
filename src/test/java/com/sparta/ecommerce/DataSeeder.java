package com.sparta.ecommerce;

import net.datafaker.Faker;
import org.junit.jupiter.api.Disabled;
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
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 대량 더미 데이터 생성기
 * EXPLAIN 분석을 위한 충분한 데이터 생성
 *
 * 실행 방법:
 * ./gradlew test --tests "DataSeeder"
 *
 * 주의: 실제 로컬 MySQL DB에 데이터가 입력됩니다!
 */
@SpringBootTest
@ActiveProfiles("local")
@Commit  // 테스트 종료 후에도 데이터 유지
//@Disabled("수동 실행용 - 필요시 @Disabled 제거 후 실행")
public class DataSeeder {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final Faker faker = new Faker(new Locale("ko"));
    private final Random random = new Random();

    // 생성할 데이터 개수
    private static final int USER_COUNT = 10_000;
    private static final int PRODUCT_COUNT = 1_000;
    private static final int ORDER_COUNT = 100_000;
    private static final int AVG_ITEMS_PER_ORDER = 2;

    // 생성된 ID 저장 (외래키 참조용)
    private List<String> userIds = new ArrayList<>();
    private List<String> productIds = new ArrayList<>();
    private List<String> orderIds = new ArrayList<>();

    @Test
    @DisplayName("대량 더미 데이터 생성 - 전체 프로세스")
    void seedAllData() {
        System.out.println("=== 데이터 생성 시작 ===");
        long startTime = System.currentTimeMillis();

        seedUsers();
        seedProducts();
        seedOrders();
        seedOrderItems();

        long endTime = System.currentTimeMillis();
        System.out.println("=== 데이터 생성 완료 ===");
        System.out.println("소요 시간: " + (endTime - startTime) / 1000.0 + "초");

        printDataCounts();
    }

    @Test
    @DisplayName("동시성 테스트용 데이터 생성")
    void seedConcurrencyTestData() {
        System.out.println("=== 동시성 테스트 데이터 생성 시작 ===");

        // 기존 테스트 데이터 삭제 (외래키 순서 고려)
        clearTestData();

        seedTestUsers();           // 테스트 유저 100명
        seedTestCoupon();          // 선착순 쿠폰 (재고 10개)
        seedTestProduct();         // 테스트 상품 (재고 10개)
        seedTestCarts();           // 장바구니 100개
        seedTestCartItems();       // 장바구니 아이템 100개
        seedTestUserCoupons();     // 유저별 쿠폰 발급 (주문 테스트용)

        System.out.println("=== 동시성 테스트 데이터 생성 완료 ===");
        printTestDataSummary();
    }

    /**
     * 기존 테스트 데이터 삭제
     */
    private void clearTestData() {
        System.out.println("기존 테스트 데이터 삭제 중...");

        // 외래키 의존성 순서대로 삭제
        jdbcTemplate.update("DELETE FROM order_items WHERE order_id IN (SELECT id FROM orders WHERE user_id LIKE 'test-user-%')");
        jdbcTemplate.update("DELETE FROM payments WHERE order_id IN (SELECT id FROM orders WHERE user_id LIKE 'test-user-%')");
        jdbcTemplate.update("DELETE FROM orders WHERE user_id LIKE 'test-user-%'");
        jdbcTemplate.update("DELETE FROM cart_items WHERE id LIKE 'test-cart-item-%'");
        jdbcTemplate.update("DELETE FROM carts WHERE id LIKE 'test-cart-%'");
        jdbcTemplate.update("DELETE FROM user_coupons WHERE id LIKE 'test-user-coupon-%'");
        jdbcTemplate.update("DELETE FROM users WHERE id LIKE 'test-user-%'");
        jdbcTemplate.update("DELETE FROM coupons WHERE id LIKE 'test-coupon-%'");
        jdbcTemplate.update("DELETE FROM products WHERE id LIKE 'test-product-%'");

        System.out.println("기존 테스트 데이터 삭제 완료");
    }

    /**
     * 동시성 테스트용 상품 생성
     * ID: test-product-1, 재고: 10개
     */
    private void seedTestProduct() {
        System.out.println("테스트 상품 생성 중...");

        String sql = "INSERT INTO products (id, name, description, price, stock, category, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        jdbcTemplate.update(sql,
                "test-product-1",
                "동시성 테스트 상품",
                "재고 10개 테스트용",
                10000,
                10,     // 재고 10개
                "테스트",
                Timestamp.valueOf(LocalDateTime.now()),
                Timestamp.valueOf(LocalDateTime.now())
        );

        System.out.println("테스트 상품 생성 완료: test-product-1 (재고 10개)");
    }

    /**
     * 동시성 테스트용 장바구니 100개 생성
     * ID: test-cart-1 ~ test-cart-100
     */
    private void seedTestCarts() {
        System.out.println("테스트 장바구니 생성 중...");

        String sql = "INSERT INTO carts (id, user_id, created_at, updated_at) VALUES (?, ?, ?, ?)";

        int cartCount = 100;

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
                return cartCount;
            }
        });

        System.out.printf("테스트 장바구니 생성 완료: %d개%n", cartCount);
    }

    /**
     * 동시성 테스트용 장바구니 아이템 100개 생성
     * 각 장바구니에 test-product-1 상품 1개씩
     */
    private void seedTestCartItems() {
        System.out.println("테스트 장바구니 아이템 생성 중...");

        String sql = "INSERT INTO cart_items (id, cart_id, product_id, quantity, added_at) VALUES (?, ?, ?, ?, ?)";

        int itemCount = 100;

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                String itemId = "test-cart-item-" + (i + 1);
                String cartId = "test-cart-" + (i + 1);

                ps.setString(1, itemId);
                ps.setString(2, cartId);
                ps.setString(3, "test-product-1");
                ps.setInt(4, 1);
                ps.setTimestamp(5, Timestamp.valueOf(LocalDateTime.now()));
            }

            @Override
            public int getBatchSize() {
                return itemCount;
            }
        });

        System.out.printf("테스트 장바구니 아이템 생성 완료: %d개%n", itemCount);
    }

    /**
     * 동시성 테스트용 유저 쿠폰 발급 (주문 시 쿠폰 사용 테스트용)
     * test-user-1 ~ test-user-10에게 test-coupon-1 발급
     */
    private void seedTestUserCoupons() {
        System.out.println("테스트 유저 쿠폰 발급 중...");

        String sql = "INSERT INTO user_coupons (id, user_id, coupon_id, issued_at, used_at, expires_at) VALUES (?, ?, ?, ?, ?, ?)";


        int couponCount = 10;  // 10명에게만 발급

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                String userCouponId = "test-user-coupon-" + (i + 1);
                String userId = "test-user-" + (i + 1);

                ps.setString(1, userCouponId);
                ps.setString(2, userId);
                ps.setString(3, "test-coupon-1");
                ps.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()));
                ps.setNull(5, java.sql.Types.TIMESTAMP);  // used_at = null
                ps.setTimestamp(6, Timestamp.valueOf(LocalDateTime.now().plusDays(30)));
            }

            @Override
            public int getBatchSize() {
                return couponCount;
            }
        });

        System.out.printf("테스트 유저 쿠폰 발급 완료: %d명%n", couponCount);
    }

    /**
     * 테스트 데이터 요약 출력
     */
    private void printTestDataSummary() {
        System.out.println("\n=== 테스트 데이터 요약 ===");
        System.out.println("유저: test-user-1 ~ test-user-100 (잔액 100만원)");
        System.out.println("쿠폰: test-coupon-1 (재고 10개)");
        System.out.println("상품: test-product-1 (재고 10개, 가격 10,000원)");
        System.out.println("장바구니: test-cart-1 ~ test-cart-100");
        System.out.println("장바구니아이템: 각 장바구니에 test-product-1 x 1개");
        System.out.println("유저쿠폰: test-user-1 ~ test-user-10에게 test-coupon-1 발급");
        System.out.println("\n=== JMeter 테스트 시나리오 ===");
        System.out.println("1. 쿠폰 발급: POST /api/coupons/test-coupon-1/issue");
        System.out.println("   - 100명 동시 요청 → 10명만 성공");
        System.out.println("2. 주문 (재고): POST /api/orders");
        System.out.println("   - {\"userId\": \"test-user-${__threadNum}\", \"couponId\": null}");
        System.out.println("   - 100명 동시 요청 → 10명만 성공 (재고 10개)");
        System.out.println("3. 주문 (쿠폰): POST /api/orders");
        System.out.println("   - {\"userId\": \"test-user-${__threadNum}\", \"couponId\": \"test-coupon-1\"}");
        System.out.println("   - 10명 동시 요청 (쿠폰 있는 유저만)");
    }

    /**
     * 동시성 테스트용 유저 100명 생성
     * ID: test-user-001 ~ test-user-100
     */
    private void seedTestUsers() {
        System.out.println("테스트 유저 생성 중...");

        String sql = "INSERT INTO users (id, name, email, balance, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)";

        int testUserCount = 100;

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                String userId = "test-user-" + (i + 1);

                ps.setString(1, userId);
                ps.setString(2, "테스트유저" + (i + 1));
                ps.setString(3, "testuser" + (i + 1) + "@test.com");
                ps.setLong(4, 1_000_000); // 100만원
                ps.setTimestamp(5, Timestamp.valueOf(LocalDateTime.now()));
                ps.setTimestamp(6, Timestamp.valueOf(LocalDateTime.now()));
            }

            @Override
            public int getBatchSize() {
                return testUserCount;
            }
        });

        System.out.printf("테스트 유저 생성 완료: %d건%n", testUserCount);
    }

    /**
     * 동시성 테스트용 쿠폰 생성
     * ID: test-coupon-1, 재고: 10개
     */
    private void seedTestCoupon() {
        System.out.println("테스트 쿠폰 생성 중...");

        String sql = "INSERT INTO coupons (id, name, discount_type, discount_value, total_quantity, issued_quantity, remaining_quantity, min_order_amount, expires_at, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        jdbcTemplate.update(sql,
                "test-coupon-1",
                "선착순 테스트 쿠폰",
                "FIXED",
                1000,
                10,     // 총 수량
                0,      // 발급된 수량
                10,     // 남은 수량
                0,      // 최소 주문 금액
                Timestamp.valueOf(LocalDateTime.now().plusDays(30)),
                Timestamp.valueOf(LocalDateTime.now()),
                Timestamp.valueOf(LocalDateTime.now())
        );

        System.out.println("테스트 쿠폰 생성 완료: test-coupon-1 (재고 10개)");
    }

    /**
     * 사용자 10,000명 생성
     */
    private void seedUsers() {
        System.out.println("User 데이터 생성 중...");
        long start = System.currentTimeMillis();

        String sql = "INSERT INTO users (id, name, email, balance, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)";

        List<String> ids = new ArrayList<>();
        for (int i = 0; i < USER_COUNT; i++) {
            ids.add(UUID.randomUUID().toString());
        }

        // 배치 처리 (1000개씩)
        int batchSize = 1000;
        for (int batch = 0; batch < USER_COUNT / batchSize; batch++) {
            int startIdx = batch * batchSize;
            int endIdx = Math.min((batch + 1) * batchSize, USER_COUNT);

            jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    int idx = startIdx + i;
                    String userId = ids.get(idx);

                    ps.setString(1, userId);
                    ps.setString(2, faker.name().fullName());
                    ps.setString(3, "user" + idx + "@test.com");
                    ps.setLong(4, random.nextInt(5_000_000)); // 0 ~ 500만원
                    ps.setTimestamp(5, Timestamp.valueOf(LocalDateTime.now()));
                    ps.setTimestamp(6, Timestamp.valueOf(LocalDateTime.now()));
                }

                @Override
                public int getBatchSize() {
                    return endIdx - startIdx;
                }
            });

            if ((batch + 1) % 5 == 0) {
                System.out.printf("  Progress: %d / %d%n", endIdx, USER_COUNT);
            }
        }

        userIds = ids;
        System.out.printf("User 생성 완료: %d건 (%.2f초)%n", USER_COUNT, (System.currentTimeMillis() - start) / 1000.0);
    }

    /**
     * 상품 1,000개 생성
     */
    private void seedProducts() {
        System.out.println("Product 데이터 생성 중...");
        long start = System.currentTimeMillis();

        String sql = "INSERT INTO products (id, name, description, price, stock, category, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        List<String> ids = new ArrayList<>();
        for (int i = 0; i < PRODUCT_COUNT; i++) {
            ids.add(UUID.randomUUID().toString());
        }

        String[] categories = {"전자제품", "의류", "식품", "도서", "생활용품", "스포츠", "완구", "뷰티"};

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                String productId = ids.get(i);

                ps.setString(1, productId);
                ps.setString(2, faker.commerce().productName());
                ps.setString(3, "상품 설명 " + i);
                ps.setLong(4, (random.nextInt(100) + 1) * 1000L); // 1,000 ~ 100,000원
                ps.setInt(5, random.nextInt(1000) + 100); // 100 ~ 1,099개
                ps.setString(6, categories[random.nextInt(categories.length)]);
                ps.setTimestamp(7, Timestamp.valueOf(LocalDateTime.now()));
                ps.setTimestamp(8, Timestamp.valueOf(LocalDateTime.now()));
            }

            @Override
            public int getBatchSize() {
                return PRODUCT_COUNT;
            }
        });

        productIds = ids;
        System.out.printf("Product 생성 완료: %d건 (%.2f초)%n", PRODUCT_COUNT, (System.currentTimeMillis() - start) / 1000.0);
    }

    /**
     * 주문 100,000건 생성 (최근 30일간 분산)
     */
    private void seedOrders() {
        System.out.println("Order 데이터 생성 중...");
        long start = System.currentTimeMillis();

        String sql = "INSERT INTO orders (id, user_id, total_amount, discount_amount, final_amount, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        List<String> ids = new ArrayList<>();
        for (int i = 0; i < ORDER_COUNT; i++) {
            ids.add(UUID.randomUUID().toString());
        }

        String[] statuses = {"COMPLETED", "COMPLETED", "COMPLETED", "PENDING", "CANCELLED"}; // COMPLETED 비율 높임

        // 배치 처리 (1000개씩)
        int batchSize = 1000;
        for (int batch = 0; batch < ORDER_COUNT / batchSize; batch++) {
            int startIdx = batch * batchSize;
            int endIdx = Math.min((batch + 1) * batchSize, ORDER_COUNT);

            jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    int idx = startIdx + i;
                    String orderId = ids.get(idx);
                    String userId = userIds.get(random.nextInt(userIds.size()));

                    long totalAmount = (random.nextInt(10) + 1) * 10000L; // 10,000 ~ 100,000원
                    long discountAmount = random.nextInt(5) * 1000L; // 0 ~ 4,000원
                    long finalAmount = totalAmount - discountAmount;

                    // 최근 30일 내 랜덤 날짜
                    LocalDateTime createdAt = LocalDateTime.now().minusDays(random.nextInt(30));

                    ps.setString(1, orderId);
                    ps.setString(2, userId);
                    ps.setLong(3, totalAmount);
                    ps.setLong(4, discountAmount);
                    ps.setLong(5, finalAmount);
                    ps.setString(6, statuses[random.nextInt(statuses.length)]);
                    ps.setTimestamp(7, Timestamp.valueOf(createdAt));
                    ps.setTimestamp(8, Timestamp.valueOf(createdAt));
                }

                @Override
                public int getBatchSize() {
                    return endIdx - startIdx;
                }
            });

            if ((batch + 1) % 10 == 0) {
                System.out.printf("  Progress: %d / %d%n", endIdx, ORDER_COUNT);
            }
        }

        orderIds = ids;
        System.out.printf("Order 생성 완료: %d건 (%.2f초)%n", ORDER_COUNT, (System.currentTimeMillis() - start) / 1000.0);
    }

    /**
     * 주문 아이템 200,000건 생성 (주문당 평균 2개)
     */
    private void seedOrderItems() {
        System.out.println("OrderItem 데이터 생성 중...");
        long start = System.currentTimeMillis();

        String sql = "INSERT INTO order_items (id, order_id, product_id, product_name, quantity, unit_price, subtotal, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        int itemCount = ORDER_COUNT * AVG_ITEMS_PER_ORDER;
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < itemCount; i++) {
            ids.add(UUID.randomUUID().toString());
        }

        // 배치 처리 (1000개씩)
        int batchSize = 1000;
        for (int batch = 0; batch < itemCount / batchSize; batch++) {
            int startIdx = batch * batchSize;
            int endIdx = Math.min((batch + 1) * batchSize, itemCount);

            jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    int idx = startIdx + i;
                    String itemId = ids.get(idx);

                    // 주문당 1~3개 아이템 (평균 2개)
                    int orderIdx = idx / AVG_ITEMS_PER_ORDER;
                    String orderId = orderIds.get(orderIdx);

                    String productId = productIds.get(random.nextInt(productIds.size()));
                    int quantity = random.nextInt(3) + 1; // 1 ~ 3개
                    long unitPrice = (random.nextInt(100) + 1) * 1000L;
                    long subtotal = unitPrice * quantity;

                    ps.setString(1, itemId);
                    ps.setString(2, orderId);
                    ps.setString(3, productId);
                    ps.setString(4, "상품명-" + productId.substring(0, 8));
                    ps.setInt(5, quantity);
                    ps.setLong(6, unitPrice);
                    ps.setLong(7, subtotal);
                    ps.setTimestamp(8, Timestamp.valueOf(LocalDateTime.now()));
                }

                @Override
                public int getBatchSize() {
                    return endIdx - startIdx;
                }
            });

            if ((batch + 1) % 20 == 0) {
                System.out.printf("  Progress: %d / %d%n", endIdx, itemCount);
            }
        }

        System.out.printf("OrderItem 생성 완료: %d건 (%.2f초)%n", itemCount, (System.currentTimeMillis() - start) / 1000.0);
    }

    /**
     *
     * 생성된 데이터 개수 확인
     */
    private void printDataCounts() {
        System.out.println("\n=== 생성된 데이터 개수 ===");
        System.out.println("User: " + jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Long.class));
        System.out.println("Product: " + jdbcTemplate.queryForObject("SELECT COUNT(*) FROM products", Long.class));
        System.out.println("Order: " + jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Long.class));
        System.out.println("OrderItem: " + jdbcTemplate.queryForObject("SELECT COUNT(*) FROM order_items", Long.class));
    }
}
