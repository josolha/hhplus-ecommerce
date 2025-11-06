package com.sparta.ecommerce.infrastructure.memory;

import com.sparta.ecommerce.domain.coupon.Coupon;
import com.sparta.ecommerce.domain.product.Product;
import com.sparta.ecommerce.domain.user.User;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 모든 Repository가 공유하는 인메모리 데이터 저장소
 * 트랜잭션 일관성을 위해 단일 저장소 사용
 */
@Component
@Getter
public class InMemoryDataStore {

    private final Map<String, Product> products = new ConcurrentHashMap<>();
    private final Map<String, Coupon> coupons = new ConcurrentHashMap<>();
    private final Map<String, User> users = new ConcurrentHashMap<>();

    /**
     * 모든 데이터 초기화 (테스트용)
     */
    public void clear() {
        products.clear();
        coupons.clear();
        users.clear();
    }
}
