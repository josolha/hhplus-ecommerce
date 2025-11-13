package com.sparta.ecommerce.domain.product.repository;

import com.sparta.ecommerce.IntegrationTestBase;
import com.sparta.ecommerce.domain.product.entity.Product;
import com.sparta.ecommerce.domain.product.vo.Stock;
import com.sparta.ecommerce.domain.product.exception.InsufficientStockException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Product Repository 통합 테스트
 * Testcontainers를 사용하여 실제 MySQL DB와 재고 관리 로직 검증
 */
@DisplayName("Product Repository 통합 테스트")
public class ProductRepositoryTest extends IntegrationTestBase {

    @Autowired
    private ProductRepository productRepository;

    @Test
    @DisplayName("Product 저장 및 조회 - JPA ID 자동 생성")
    void saveAndFind() {
        // given
        Product product = Product.builder()
                .name("테스트상품")
                .price(10000L)
                .stock(new Stock(100))
                .build();

        // when
        Product savedProduct = productRepository.save(product);
        entityManager.flush();
        entityManager.clear();

        // then
        assertThat(savedProduct.getProductId()).isNotNull();  // ID 자동 생성 검증

        Product foundProduct = productRepository.findById(savedProduct.getProductId()).get();
        assertThat(foundProduct.getName()).isEqualTo("테스트상품");
        assertThat(foundProduct.getPrice()).isEqualTo(10000L);
        assertThat(foundProduct.getStock().quantity()).isEqualTo(100);
    }

    @Test
    @DisplayName("Product 재고 감소 - 업데이트 검증")
    void decreaseStock() {
        // given
        Product product = Product.builder()
                .name("재고테스트상품")
                .price(10000L)
                .stock(new Stock(100))
                .build();
        productRepository.save(product);
        entityManager.flush();
        entityManager.clear();

        // when
        Product foundProduct = productRepository.findById(product.getProductId()).get();
        foundProduct.reserveStock(30);  // 30개 차감
        productRepository.save(foundProduct);
        entityManager.flush();
        entityManager.clear();

        // then
        Product updatedProduct = productRepository.findById(product.getProductId()).get();
        assertThat(updatedProduct.getStock().quantity()).isEqualTo(70);
    }

    @Test
    @DisplayName("Product 재고 여러 번 감소 - 업데이트 검증")
    void multipleDecrease() {
        // given
        Product product = Product.builder()
                .name("재고감소상품")
                .price(10000L)
                .stock(new Stock(100))
                .build();
        productRepository.save(product);
        entityManager.flush();
        entityManager.clear();

        // when - 두 번 차감
        Product foundProduct = productRepository.findById(product.getProductId()).get();
        foundProduct.reserveStock(30);  // 30개 차감
        productRepository.save(foundProduct);
        entityManager.flush();
        entityManager.clear();

        Product foundProduct2 = productRepository.findById(product.getProductId()).get();
        foundProduct2.reserveStock(20);  // 20개 더 차감
        productRepository.save(foundProduct2);
        entityManager.flush();
        entityManager.clear();

        // then
        Product updatedProduct = productRepository.findById(product.getProductId()).get();
        assertThat(updatedProduct.getStock().quantity()).isEqualTo(50);  // 100 - 30 - 20
    }

    @Test
    @DisplayName("Product 재고 부족 시 예외 발생")
    void insufficientStock() {
        // given
        Product product = Product.builder()
                .name("품절상품")
                .price(10000L)
                .stock(new Stock(5))
                .build();
        productRepository.save(product);
        entityManager.flush();
        entityManager.clear();

        // when
        Product foundProduct = productRepository.findById(product.getProductId()).get();

        // then
        assertThatThrownBy(() -> foundProduct.reserveStock(10))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("재고가 부족합니다");
    }

    @Test
    @DisplayName("Product 재고 0일 때 추가 차감 불가")
    void zeroStock() {
        // given
        Product product = Product.builder()
                .name("재고0상품")
                .price(10000L)
                .stock(new Stock(0))
                .build();
        productRepository.save(product);
        entityManager.flush();
        entityManager.clear();

        // when
        Product foundProduct = productRepository.findById(product.getProductId()).get();

        // then
        assertThat(foundProduct.getStock().quantity()).isEqualTo(0);
        assertThatThrownBy(() -> foundProduct.reserveStock(1))
                .isInstanceOf(InsufficientStockException.class);
    }


    @Test
    @DisplayName("Product 조회 시 createdAt/updatedAt 자동 생성 검증")
    void auditFields() {
        // given
        Product product = Product.builder()
                .name("감사필드상품")
                .price(10000L)
                .stock(new Stock(100))
                .build();

        // when
        Product savedProduct = productRepository.save(product);
        entityManager.flush();
        entityManager.clear();

        // then
        Product foundProduct = productRepository.findById(savedProduct.getProductId()).get();
        assertThat(foundProduct.getCreatedAt()).isNotNull();
        assertThat(foundProduct.getUpdatedAt()).isNotNull();
    }
}
