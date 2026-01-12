package com.sparta.ecommerce;

import net.datafaker.Faker;
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

/**
 * 실제 운영 환경 데이터 생성기 (UUID 사용)
 * EXPLAIN 분석, 인덱스 성능 테스트, 쿼리 최적화를 위한 대량 더미 데이터 생성
 *
 * 실행 방법:
 * ./gradlew test --tests "RealDataSeeder.seedAllData"
 */
@SpringBootTest
@ActiveProfiles("local")
@Commit
public class RealDataSeeder {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final Faker faker = new Faker(new Locale("ko"));
    private final Random random = new Random();

    // 생성할 데이터 개수
    private static final int USER_COUNT = 10_000;
    private static final int PRODUCT_COUNT = 1_000;
    private static final int COUPON_COUNT = 50;
    private static final int ORDER_COUNT = 50_000;
    private static final int AVG_ITEMS_PER_ORDER = 2;

    // 생성된 ID 저장 (외래키 참조용)
    private List<String> userIds = new ArrayList<>();
    private List<String> productIds = new ArrayList<>();
    private List<String> couponIds = new ArrayList<>();
    private List<String> orderIds = new ArrayList<>();
    private Map<String, String> userIdToCartId = new HashMap<>();  // userId -> cartId 매핑

    @Test
    @DisplayName("실제 운영 데이터 생성 - UUID 사용")
    void seedAllData() {
        System.out.println("=== 실제 운영 데이터 생성 시작 ===");
        long startTime = System.currentTimeMillis();

        // 기존 데이터 전체 삭제
        clearAllData();

        seedUsers();
        seedProducts();
        seedCoupons();
        seedUserCoupons();        // 쿠폰 발급 이력
        seedBalanceHistory();     // 잔액 충전/사용 이력
        seedCarts();
        seedCartItems();          // 장바구니 아이템
        seedOrders();
        seedOrderItems();
        seedPayments();

        long endTime = System.currentTimeMillis();
        System.out.println("=== 실제 운영 데이터 생성 완료 ===");
        System.out.println("소요 시간: " + (endTime - startTime) / 1000.0 + "초");

        printDataCounts();
    }

    /**
     * 기존 데이터 전체 삭제 (외래키 제약 순서 고려)
     */
    private void clearAllData() {
        System.out.println("기존 데이터 삭제 중...");
        long start = System.currentTimeMillis();

        // 외래키 제약 순서에 따라 삭제 (자식 → 부모)
        jdbcTemplate.execute("DELETE FROM payments");
        jdbcTemplate.execute("DELETE FROM order_items");
        jdbcTemplate.execute("DELETE FROM orders");
        jdbcTemplate.execute("DELETE FROM cart_items");
        jdbcTemplate.execute("DELETE FROM carts");
        jdbcTemplate.execute("DELETE FROM balance_history");
        jdbcTemplate.execute("DELETE FROM user_coupons");
        jdbcTemplate.execute("DELETE FROM coupons");
        jdbcTemplate.execute("DELETE FROM products");
        jdbcTemplate.execute("DELETE FROM users");

        System.out.printf("기존 데이터 삭제 완료 (%.2f초)%n", (System.currentTimeMillis() - start) / 1000.0);
    }

    /**
     * 사용자 10,000명 생성 (UUID)
     */
    private void seedUsers() {
        System.out.println("User 데이터 생성 중...");
        long start = System.currentTimeMillis();

        String sql = "INSERT INTO users (id, email, name, balance, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)";

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
                    ps.setString(2, faker.internet().emailAddress());
                    ps.setString(3, faker.name().fullName());
                    ps.setLong(4, random.nextInt(5_000_000) + 100_000); // 10만 ~ 510만원
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
     * 상품 1,000개 생성 (UUID)
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
                ps.setString(3, faker.lorem().sentence(10));
                ps.setLong(4, (random.nextInt(200) + 1) * 1000L); // 1,000 ~ 200,000원
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
     * 쿠폰 50개 생성 (UUID)
     */
    private void seedCoupons() {
        System.out.println("Coupon 데이터 생성 중...");
        long start = System.currentTimeMillis();

        String sql = "INSERT INTO coupons (id, name, discount_type, discount_value, total_quantity, issued_quantity, remaining_quantity, min_order_amount, expires_at, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        List<String> ids = new ArrayList<>();
        for (int i = 0; i < COUPON_COUNT; i++) {
            ids.add(UUID.randomUUID().toString());
        }

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                String couponId = ids.get(i);
                String discountType = random.nextBoolean() ? "FIXED" : "PERCENT";
                long discountValue = discountType.equals("FIXED") ?
                        random.nextInt(10) * 1000L + 1000 : // 1,000 ~ 10,000원
                        random.nextInt(20) + 5; // 5 ~ 24%

                int totalQuantity = random.nextInt(1000) + 100;

                ps.setString(1, couponId);
                ps.setString(2, faker.commerce().promotionCode() + " 쿠폰");
                ps.setString(3, discountType);
                ps.setLong(4, discountValue);
                ps.setInt(5, totalQuantity);
                ps.setInt(6, 0);  // 발급된 수량
                ps.setInt(7, totalQuantity);  // 남은 수량
                ps.setLong(8, random.nextInt(5) * 10000L);  // 최소 주문 금액 0 ~ 40,000원
                ps.setTimestamp(9, Timestamp.valueOf(LocalDateTime.now().plusDays(random.nextInt(365)))); // 1년 이내 만료
                ps.setTimestamp(10, Timestamp.valueOf(LocalDateTime.now()));
                ps.setTimestamp(11, Timestamp.valueOf(LocalDateTime.now()));
            }

            @Override
            public int getBatchSize() {
                return COUPON_COUNT;
            }
        });

        couponIds = ids;
        System.out.printf("Coupon 생성 완료: %d건 (%.2f초)%n", COUPON_COUNT, (System.currentTimeMillis() - start) / 1000.0);
    }

    /**
     * 사용자 쿠폰 발급 이력 생성
     * 전체 사용자의 30%가 쿠폰을 1~3개 보유
     */
    private void seedUserCoupons() {
        System.out.println("UserCoupon 데이터 생성 중...");
        long start = System.currentTimeMillis();

        String sql = "INSERT INTO user_coupons (id, user_id, coupon_id, issued_at, used_at, expires_at) " +
                "VALUES (?, ?, ?, ?, ?, ?)";

        // 전체 사용자의 30%가 쿠폰 보유
        int userCouponCount = (int) (USER_COUNT * 0.3);
        List<String> userCouponIds = new ArrayList<>();

        // 배치 처리
        int batchSize = 1000;
        for (int batch = 0; batch < (userCouponCount + batchSize - 1) / batchSize; batch++) {
            int startIdx = batch * batchSize;
            int endIdx = Math.min((batch + 1) * batchSize, userCouponCount);

            final int finalStartIdx = startIdx;
            final int finalEndIdx = endIdx;

            jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    int idx = finalStartIdx + i;
                    String userCouponId = UUID.randomUUID().toString();
                    String userId = userIds.get(random.nextInt(userIds.size()));
                    String couponId = couponIds.get(random.nextInt(couponIds.size()));

                    LocalDateTime issuedAt = LocalDateTime.now().minusDays(random.nextInt(30));
                    LocalDateTime expiresAt = issuedAt.plusDays(30);

                    // 50% 확률로 사용됨
                    LocalDateTime usedAt = random.nextBoolean() ? issuedAt.plusDays(random.nextInt(15)) : null;

                    ps.setString(1, userCouponId);
                    ps.setString(2, userId);
                    ps.setString(3, couponId);
                    ps.setTimestamp(4, Timestamp.valueOf(issuedAt));
                    ps.setTimestamp(5, usedAt != null ? Timestamp.valueOf(usedAt) : null);
                    ps.setTimestamp(6, Timestamp.valueOf(expiresAt));
                }

                @Override
                public int getBatchSize() {
                    return finalEndIdx - finalStartIdx;
                }
            });
        }

        System.out.printf("UserCoupon 생성 완료: %d건 (%.2f초)%n", userCouponCount, (System.currentTimeMillis() - start) / 1000.0);
    }

    /**
     * 잔액 충전/사용 이력 생성
     * 각 사용자당 평균 5건의 이력
     */
    private void seedBalanceHistory() {
        System.out.println("BalanceHistory 데이터 생성 중...");
        long start = System.currentTimeMillis();

        String sql = "INSERT INTO balance_history (user_id, transaction_id, amount, previous_balance, current_balance, payment_method, charged_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";

        int historyCount = USER_COUNT * 5;

        // 배치 처리
        int batchSize = 1000;
        for (int batch = 0; batch < (historyCount + batchSize - 1) / batchSize; batch++) {
            int startIdx = batch * batchSize;
            int endIdx = Math.min((batch + 1) * batchSize, historyCount);

            final int finalStartIdx = startIdx;
            final int finalEndIdx = endIdx;

            jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    int idx = finalStartIdx + i;
                    String userId = userIds.get(random.nextInt(userIds.size()));
                    String transactionId = "TXN-" + UUID.randomUUID().toString();  // 고유한 거래 ID
                    long amount = (random.nextInt(100) + 1) * 1000L; // 1,000 ~ 100,000원

                    // 이전 잔액과 현재 잔액 (충전 시뮬레이션)
                    long previousBalance = (random.nextInt(500) + 10) * 1000L;
                    long currentBalance = previousBalance + amount;

                    String paymentMethod = random.nextBoolean() ? "CARD" : "BANK_TRANSFER";
                    LocalDateTime chargedAt = LocalDateTime.now().minusDays(random.nextInt(90));

                    ps.setString(1, userId);
                    ps.setString(2, transactionId);  // transaction_id 추가
                    ps.setLong(3, amount);
                    ps.setLong(4, previousBalance);
                    ps.setLong(5, currentBalance);
                    ps.setString(6, paymentMethod);
                    ps.setTimestamp(7, Timestamp.valueOf(chargedAt));
                }

                @Override
                public int getBatchSize() {
                    return finalEndIdx - finalStartIdx;
                }
            });
        }

        System.out.printf("BalanceHistory 생성 완료: %d건 (%.2f초)%n", historyCount, (System.currentTimeMillis() - start) / 1000.0);
    }

    /**
     * 장바구니 생성 (사용자당 1개)
     */
    private void seedCarts() {
        System.out.println("Cart 데이터 생성 중...");
        long start = System.currentTimeMillis();

        String sql = "INSERT INTO carts (id, user_id, created_at, updated_at) VALUES (?, ?, ?, ?)";

        // 배치 처리 (1000개씩)
        int batchSize = 1000;
        for (int batch = 0; batch < USER_COUNT / batchSize; batch++) {
            int startIdx = batch * batchSize;
            int endIdx = Math.min((batch + 1) * batchSize, USER_COUNT);

            // 각 배치에서 생성할 cartId를 미리 생성하고 매핑 저장
            List<String> batchCartIds = new ArrayList<>();
            for (int i = startIdx; i < endIdx; i++) {
                String cartId = UUID.randomUUID().toString();
                String userId = userIds.get(i);
                batchCartIds.add(cartId);
                userIdToCartId.put(userId, cartId);  // 매핑 저장
            }

            jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    int idx = startIdx + i;
                    String cartId = batchCartIds.get(i);
                    String userId = userIds.get(idx);

                    ps.setString(1, cartId);
                    ps.setString(2, userId);
                    ps.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
                    ps.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()));
                }

                @Override
                public int getBatchSize() {
                    return endIdx - startIdx;
                }
            });
        }

        System.out.printf("Cart 생성 완료: %d건 (%.2f초)%n", USER_COUNT, (System.currentTimeMillis() - start) / 1000.0);
    }

    /**
     * 장바구니 아이템 생성
     * 각 장바구니에 3개 상품 (중복 없음)
     */
    private void seedCartItems() {
        System.out.println("CartItem 데이터 생성 중...");
        long start = System.currentTimeMillis();

        String sql = "INSERT INTO cart_items (cart_id, product_id, quantity, added_at) " +
                "VALUES (?, ?, ?, ?)";

        // 각 장바구니당 3개 상품 = 총 30,000개 아이템
        final int ITEMS_PER_CART = 3;
        final int TOTAL_ITEMS = USER_COUNT * ITEMS_PER_CART;

        // 배치 처리
        int batchSize = 1000;
        for (int batch = 0; batch < (TOTAL_ITEMS + batchSize - 1) / batchSize; batch++) {
            int startIdx = batch * batchSize;
            int endIdx = Math.min((batch + 1) * batchSize, TOTAL_ITEMS);

            final int finalStartIdx = startIdx;
            final int finalEndIdx = endIdx;

            jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    int idx = finalStartIdx + i;

                    int cartIdx = idx / ITEMS_PER_CART;  // 장바구니 번호
                    int itemIdx = idx % ITEMS_PER_CART;  // 아이템 순서 (0~2)

                    String userId = userIds.get(cartIdx % userIds.size());

                    // userIdToCartId 매핑에서 실제 cartId 가져오기
                    String cartId = userIdToCartId.get(userId);
                    if (cartId == null) {
                        throw new IllegalStateException("Cart not found for userId: " + userId);
                    }

                    // 각 장바구니에 서로 다른 상품 (중복 없음)
                    int productIndex = (cartIdx * ITEMS_PER_CART + itemIdx) % productIds.size();
                    String productId = productIds.get(productIndex);

                    int quantity = 2;  // 고정 수량 2개

                    LocalDateTime addedAt = LocalDateTime.now().minusDays(random.nextInt(7));

                    ps.setString(1, cartId);
                    ps.setString(2, productId);
                    ps.setInt(3, quantity);
                    ps.setTimestamp(4, Timestamp.valueOf(addedAt));
                }

                @Override
                public int getBatchSize() {
                    return finalEndIdx - finalStartIdx;
                }
            });
        }

        System.out.printf("CartItem 생성 완료: %d건 (%.2f초)%n", TOTAL_ITEMS, (System.currentTimeMillis() - start) / 1000.0);
    }

    /**
     * 주문 50,000건 생성 (최근 90일간 분산)
     */
    private void seedOrders() {
        System.out.println("Order 데이터 생성 중...");
        long start = System.currentTimeMillis();

        String sql = "INSERT INTO orders (id, user_id, user_coupon_id, total_amount, discount_amount, final_amount, status, paid_at, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        List<String> ids = new ArrayList<>();
        for (int i = 0; i < ORDER_COUNT; i++) {
            ids.add(UUID.randomUUID().toString());
        }

        String[] statuses = {"COMPLETED", "COMPLETED", "COMPLETED", "PENDING", "CANCELLED"};

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
                    String status = statuses[random.nextInt(statuses.length)];

                    long totalAmount = (random.nextInt(20) + 1) * 10000L; // 10,000 ~ 200,000원
                    long discountAmount = random.nextInt(10) * 1000L; // 0 ~ 9,000원
                    long finalAmount = totalAmount - discountAmount;

                    // 최근 90일 내 랜덤 날짜
                    LocalDateTime createdAt = LocalDateTime.now().minusDays(random.nextInt(90));

                    ps.setString(1, orderId);
                    ps.setString(2, userId);
                    ps.setString(3, null);  // 쿠폰 사용 안 함
                    ps.setLong(4, totalAmount);
                    ps.setLong(5, discountAmount);
                    ps.setLong(6, finalAmount);
                    ps.setString(7, status);
                    ps.setTimestamp(8, status.equals("COMPLETED") ? Timestamp.valueOf(createdAt) : null);
                    ps.setTimestamp(9, Timestamp.valueOf(createdAt));
                    ps.setTimestamp(10, Timestamp.valueOf(createdAt));
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
     * 주문 아이템 생성 (주문당 평균 2개)
     */
    private void seedOrderItems() {
        System.out.println("OrderItem 데이터 생성 중...");
        long start = System.currentTimeMillis();

        String sql = "INSERT INTO order_items (order_id, product_id, product_name, quantity, unit_price, subtotal, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";

        int itemCount = ORDER_COUNT * AVG_ITEMS_PER_ORDER;

        // 배치 처리 (1000개씩)
        int batchSize = 1000;
        for (int batch = 0; batch < itemCount / batchSize; batch++) {
            int startIdx = batch * batchSize;
            int endIdx = Math.min((batch + 1) * batchSize, itemCount);

            jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    int idx = startIdx + i;

                    int orderIdx = idx / AVG_ITEMS_PER_ORDER;
                    String orderId = orderIds.get(orderIdx);

                    String productId = productIds.get(random.nextInt(productIds.size()));
                    int quantity = random.nextInt(3) + 1; // 1 ~ 3개
                    long unitPrice = (random.nextInt(200) + 1) * 1000L;
                    long subtotal = unitPrice * quantity;

                    ps.setString(1, orderId);
                    ps.setString(2, productId);
                    ps.setString(3, faker.commerce().productName());
                    ps.setInt(4, quantity);
                    ps.setLong(5, unitPrice);
                    ps.setLong(6, subtotal);
                    ps.setTimestamp(7, Timestamp.valueOf(LocalDateTime.now()));
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
     * 결제 데이터 생성 (완료된 주문에 대해서만)
     */
    private void seedPayments() {
        System.out.println("Payment 데이터 생성 중...");
        long start = System.currentTimeMillis();

        // COMPLETED 상태인 주문만 가져오기
        List<Map<String, Object>> completedOrders = jdbcTemplate.queryForList(
                "SELECT id, user_id, final_amount, paid_at FROM orders WHERE status = 'COMPLETED'"
        );

        String sql = "INSERT INTO payments (id, order_id, user_id, amount, method, status, pg_transaction_id, paid_at, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        // 배치 처리
        int batchSize = 1000;
        int totalPayments = completedOrders.size();

        for (int batch = 0; batch < (totalPayments + batchSize - 1) / batchSize; batch++) {
            int startIdx = batch * batchSize;
            int endIdx = Math.min((batch + 1) * batchSize, totalPayments);

            final int finalStartIdx = startIdx;
            final int finalEndIdx = endIdx;

            jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    int idx = finalStartIdx + i;
                    Map<String, Object> order = completedOrders.get(idx);

                    String paymentId = UUID.randomUUID().toString();
                    String orderId = (String) order.get("id");
                    String userId = (String) order.get("user_id");
                    long amount = ((Number) order.get("final_amount")).longValue();

                    // LocalDateTime을 Timestamp로 변환
                    Object paidAtObj = order.get("paid_at");
                    Timestamp paidAt;
                    if (paidAtObj instanceof LocalDateTime) {
                        paidAt = Timestamp.valueOf((LocalDateTime) paidAtObj);
                    } else if (paidAtObj instanceof Timestamp) {
                        paidAt = (Timestamp) paidAtObj;
                    } else {
                        paidAt = Timestamp.valueOf(LocalDateTime.now());
                    }

                    ps.setString(1, paymentId);
                    ps.setString(2, orderId);
                    ps.setString(3, userId);
                    ps.setLong(4, amount);
                    ps.setString(5, "BALANCE");  // 잔액 결제
                    ps.setString(6, "COMPLETED");
                    ps.setString(7, "PG-" + UUID.randomUUID().toString().substring(0, 8));
                    ps.setTimestamp(8, paidAt);
                    ps.setTimestamp(9, paidAt);
                    ps.setTimestamp(10, paidAt);
                }

                @Override
                public int getBatchSize() {
                    return finalEndIdx - finalStartIdx;
                }
            });
        }

        System.out.printf("Payment 생성 완료: %d건 (%.2f초)%n", totalPayments, (System.currentTimeMillis() - start) / 1000.0);
    }

    /**
     * 생성된 데이터 개수 확인
     */
    private void printDataCounts() {
        System.out.println("\n=== 생성된 실제 데이터 개수 ===");
        System.out.println("User: " + jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Long.class));
        System.out.println("Product: " + jdbcTemplate.queryForObject("SELECT COUNT(*) FROM products", Long.class));
        System.out.println("Coupon: " + jdbcTemplate.queryForObject("SELECT COUNT(*) FROM coupons", Long.class));
        System.out.println("UserCoupon: " + jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_coupons", Long.class));
        System.out.println("BalanceHistory: " + jdbcTemplate.queryForObject("SELECT COUNT(*) FROM balance_history", Long.class));
        System.out.println("Cart: " + jdbcTemplate.queryForObject("SELECT COUNT(*) FROM carts", Long.class));
        System.out.println("CartItem: " + jdbcTemplate.queryForObject("SELECT COUNT(*) FROM cart_items", Long.class));
        System.out.println("Order: " + jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Long.class));
        System.out.println("OrderItem: " + jdbcTemplate.queryForObject("SELECT COUNT(*) FROM order_items", Long.class));
        System.out.println("Payment: " + jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payments", Long.class));
        System.out.println("\n이 데이터는 인덱스 성능 테스트, EXPLAIN 분석, 쿼리 최적화 등에 활용할 수 있습니다.");
    }
}
