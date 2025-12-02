package com.sparta.ecommerce.application.product;

import com.sparta.ecommerce.application.product.dto.ProductResponse;
import com.sparta.ecommerce.domain.product.entity.Product;
import com.sparta.ecommerce.domain.product.repository.ProductRepository;
import com.sparta.ecommerce.domain.product.exception.ProductNotFoundException;
import lombok.AllArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.sparta.ecommerce.common.config.CacheConfig.PRODUCT_DETAIL;

/**
 * 상품 상세 조회 UseCase
 *
 * [캐시 전략]
 * - Cache-Aside 패턴
 * - TTL: 10분 (자주 조회, 변경 드묾)
 * - 키: productDetail::{productId}
 */
@Service
@AllArgsConstructor
public class GetProductDetailUseCase {

    private final ProductRepository productRepository;

    @Cacheable(cacheNames = PRODUCT_DETAIL, key = "#productId")
    @Transactional(readOnly = true)
    public ProductResponse execute(String productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        return ProductResponse.from(product);
    }
}
