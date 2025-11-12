package com.sparta.ecommerce.application.product;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import com.sparta.ecommerce.application.product.dto.ProductStockResponse;
import com.sparta.ecommerce.domain.product.entity.Product;
import com.sparta.ecommerce.domain.product.repository.ProductRepository;
import com.sparta.ecommerce.domain.product.exception.ProductNotFoundException;
import com.sparta.ecommerce.domain.product.vo.Stock;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("상품 재고 확인 UseCase 테스트")
class GetProductStockUseCaseTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private GetProductStockUseCase getProductStockUseCase;

    @Test
    @DisplayName("재고가 있는 상품의 재고 정보를 정확히 반환한다")
    void 재고_있음_확인() {
        // given
        String productId = "P001";
        Product product = Product.builder()
                .productId("P001")
                .name("노트북")
                .price(1500000)
                .stock(new Stock(10))
                .category("전자제품")
                .build();

        given(productRepository.findById(productId))
                .willReturn(Optional.of(product));

        // when
        ProductStockResponse response = getProductStockUseCase.execute(productId);

        // then
        assertThat(response).isNotNull();
        assertThat(response.productId()).isEqualTo("P001");
        assertThat(response.stock()).isEqualTo(10);
        assertThat(response.available()).isTrue();  // 재고 있음

        verify(productRepository, times(1)).findById(productId);
    }

    @Test
    @DisplayName("재고가 0인 상품은 available이 false를 반환한다")
    void 재고_없음_확인() {
        // given
        String productId = "P002";
        Product product = Product.builder()
                .productId("P002")
                .name("품절 상품")
                .price(50000)
                .stock(new Stock(0))  // 재고 0
                .category("전자제품")
                .build();

        given(productRepository.findById(productId))
                .willReturn(Optional.of(product));

        // when
        ProductStockResponse response = getProductStockUseCase.execute(productId);

        // then
        assertThat(response).isNotNull();
        assertThat(response.productId()).isEqualTo("P002");
        assertThat(response.stock()).isEqualTo(0);
        assertThat(response.available()).isFalse();  // 재고 없음

        verify(productRepository, times(1)).findById(productId);
    }

    @Test
    @DisplayName("존재하지 않는 상품 ID로 조회하면 ProductNotFoundException을 던진다")
    void 상품_없음_예외() {
        // given
        String productId = "INVALID_ID";
        given(productRepository.findById(productId))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> getProductStockUseCase.execute(productId))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("상품을 찾을 수 없습니다");

        verify(productRepository, times(1)).findById(productId);
    }
}