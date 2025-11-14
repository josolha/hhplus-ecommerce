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
