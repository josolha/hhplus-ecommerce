package com.sparta.ecommerce.domain.order.service;

import com.sparta.ecommerce.domain.cart.entity.CartItem;
import com.sparta.ecommerce.domain.order.entity.OrderItem;
import com.sparta.ecommerce.domain.product.entity.Product;
import com.sparta.ecommerce.domain.product.exception.InsufficientStockException;
import com.sparta.ecommerce.domain.product.exception.ProductNotFoundException;
import com.sparta.ecommerce.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 주문 항목 준비 서비스
 *
 * 장바구니 아이템으로부터 주문 항목을 준비하는 도메인 서비스
 * - 재고 확인 (분산 락 환경에서 실행)
 * - OrderItem 생성
 * - 총 금액 계산
 */
@Service
@RequiredArgsConstructor
public class OrderItemPreparationService {

    private final ProductRepository productRepository;

    /**
     * 주문 항목 준비
     *
     * @param cartItems 장바구니 아이템 목록
     * @return 준비된 주문 정보
     */
    public OrderPreparation prepare(List<CartItem> cartItems) {
        List<OrderItem> orderItems = new ArrayList<>();
        List<Product> lockedProducts = new ArrayList<>();
        long totalAmount = 0;

        for (CartItem cartItem : cartItems) {
            // 상품 조회 (분산 락 환경에서 일반 조회)
            Product product = productRepository.findById(cartItem.getProductId())
                    .orElseThrow(() -> new ProductNotFoundException(cartItem.getProductId()));

            // 재고 확인
            if (!product.canAddToCart(cartItem.getQuantity())) {
                throw new InsufficientStockException(
                        "상품 재고가 부족합니다: " + product.getName() +
                        " (요청: " + cartItem.getQuantity() + ", 재고: " + product.getStock().getQuantity() + ")"
                );
            }

            // 락 걸린 상품 저장 (재고 차감 시 재사용)
            lockedProducts.add(product);

            // OrderItem 생성
            long price = (long) product.getPrice();
            long subtotal = price * cartItem.getQuantity();

            OrderItem orderItem = OrderItem.builder()
                    .productId(product.getProductId())
                    .productName(product.getName())
                    .unitPrice(price)
                    .quantity(cartItem.getQuantity())
                    .subtotal(subtotal)
                    .build();

            orderItems.add(orderItem);
            totalAmount += subtotal;
        }

        return new OrderPreparation(orderItems, lockedProducts, totalAmount);
    }

    /**
     * 주문 준비 결과
     */
    public record OrderPreparation(
            List<OrderItem> orderItems,
            List<Product> lockedProducts,
            long totalAmount
    ) {}
}
