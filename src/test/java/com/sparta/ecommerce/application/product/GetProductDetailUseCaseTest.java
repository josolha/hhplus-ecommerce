package com.sparta.ecommerce.application.product;

import com.sparta.ecommerce.application.product.dto.ProductResponse;
import com.sparta.ecommerce.application.product.usecase.GetProductDetailUseCase;
import com.sparta.ecommerce.domain.product.entity.Product;
import com.sparta.ecommerce.domain.product.repository.ProductRepository;
import com.sparta.ecommerce.domain.product.exception.ProductNotFoundException;
import com.sparta.ecommerce.domain.product.vo.Stock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("상품 상세 조회 UseCase 테스트")
class GetProductDetailUseCaseTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private GetProductDetailUseCase getProductDetailUseCase;

    private Product testProduct;

    @BeforeEach
    void setUp() {
        testProduct = Product.builder()
                .productId("P001")
                .name("노트북")
                .price(1500000)
                .stock(new Stock(10))
                .category("전자제품")
                .description("고성능 노트북")
                .build();
    }

    @Test
    @DisplayName("상품이 존재하면 상품 상세 정보를 반환한다")
    void 상품_상세_조회_성공() {
        // Given
        String productId = "P001";
        given(productRepository.findById(productId))
                .willReturn(Optional.of(testProduct));

        // When
        ProductResponse response = getProductDetailUseCase.execute(productId);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.productId()).isEqualTo("P001");
        assertThat(response.name()).isEqualTo("노트북");
        assertThat(response.price()).isEqualTo(1500000);
        assertThat(response.stock()).isEqualTo(10);
        assertThat(response.category()).isEqualTo("전자제품");

        // Verify
        verify(productRepository, times(1)).findById(productId);
    }

    @Test
    @DisplayName("상품이 존재하지 않으면 ProductNotFoundException을 던진다")
    void 상품_없을때_예외_발생() {
        // Given
        String productId = "INVALID_ID";
        given(productRepository.findById(productId))
                .willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> getProductDetailUseCase.execute(productId))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("상품을 찾을 수 없습니다");

        // Verify
        verify(productRepository, times(1)).findById(productId);
    }

    @Test
    @DisplayName("ProductResponse가 Product로부터 올바르게 변환된다")
    void ProductResponse_변환_검증() {
        // Given
        String productId = "P001";
        given(productRepository.findById(productId))
                .willReturn(Optional.of(testProduct));

        // When
        ProductResponse response = getProductDetailUseCase.execute(productId);

        // Then
        assertThat(response.productId()).isEqualTo(testProduct.getProductId());
        assertThat(response.name()).isEqualTo(testProduct.getName());
        assertThat(response.price()).isEqualTo(testProduct.getPrice());
        assertThat(response.stock()).isEqualTo(testProduct.getStock().getQuantity());
        assertThat(response.category()).isEqualTo(testProduct.getCategory());
    }
}
