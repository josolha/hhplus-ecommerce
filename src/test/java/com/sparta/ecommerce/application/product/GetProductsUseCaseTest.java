package com.sparta.ecommerce.application.product;

import com.sparta.ecommerce.application.product.dto.ProductResponse;
import com.sparta.ecommerce.domain.product.Product;
import com.sparta.ecommerce.domain.product.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("상품 목록 조회 UseCase 테스트")
class GetProductsUseCaseTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private GetProductsUseCase getProductsUseCase;

    private List<Product> testProducts;

    @BeforeEach
    void setUp() {
        testProducts = List.of(
                Product.builder()
                        .productId("P001").name("노트북").price(1500000)
                        .stock(10).category("전자제품").build(),
                Product.builder()
                        .productId("P002").name("무선 마우스").price(35000)
                        .stock(50).category("전자제품").build(),
                Product.builder()
                        .productId("P003").name("티셔츠").price(29000)
                        .stock(100).category("의류").build()
        );
    }

    @Test
    @DisplayName("카테고리와 정렬 없이 전체 상품을 조회한다")
    void 전체_상품_조회() {
        // given
        given(productRepository.findAll()).willReturn(testProducts);

        // when
        List<ProductResponse> responses = getProductsUseCase.execute(null, null);

        // then
        assertThat(responses).hasSize(3);
        assertThat(responses).extracting("name")
                .containsExactlyInAnyOrder("노트북", "무선 마우스", "티셔츠");

        verify(productRepository, times(1)).findAll();
        verify(productRepository, never()).findByCategory(anyString());
    }

    @Test
    @DisplayName("특정 카테고리의 상품만 조회한다")
    void 카테고리_필터링() {
        // given
        List<Product> electronics = testProducts.stream()
                .filter(p -> "전자제품".equals(p.getCategory()))
                .toList();
        given(productRepository.findByCategory("전자제품")).willReturn(electronics);

        // when
        List<ProductResponse> responses = getProductsUseCase.execute("전자제품", null);

        // then
        assertThat(responses).hasSize(2);
        assertThat(responses).allMatch(r -> r.category().equals("전자제품"));
        assertThat(responses).extracting("name")
                .containsExactlyInAnyOrder("노트북", "무선 마우스");

        verify(productRepository, times(1)).findByCategory("전자제품");
        verify(productRepository, never()).findAll();
    }

    @Test
    @DisplayName("가격순으로 정렬하여 조회한다")
    void 가격순_정렬() {
        // given
        List<Product> sortedProducts = testProducts.stream()
                .sorted((p1, p2) -> Double.compare(p1.getPrice(), p2.getPrice()))
                .toList();
        given(productRepository.findAllByOrderByPriceAsc()).willReturn(sortedProducts);

        // when
        List<ProductResponse> responses = getProductsUseCase.execute(null, "price");

        // then
        assertThat(responses).hasSize(3);
        assertThat(responses.get(0).price()).isLessThanOrEqualTo(responses.get(1).price());
        assertThat(responses.get(1).price()).isLessThanOrEqualTo(responses.get(2).price());
        assertThat(responses.get(0).name()).isEqualTo("티셔츠"); // 가장 저렴
        assertThat(responses.get(2).name()).isEqualTo("노트북"); // 가장 비쌈

        verify(productRepository, times(1)).findAllByOrderByPriceAsc();
        verify(productRepository, never()).findAll();
    }

    @Test
    @DisplayName("카테고리 필터링과 가격순 정렬을 동시에 적용한다")
    void 카테고리_필터링_및_가격순_정렬() {
        // given
        List<Product> electronicsAndSorted = testProducts.stream()
                .filter(p -> "전자제품".equals(p.getCategory()))
                .sorted((p1, p2) -> Double.compare(p1.getPrice(), p2.getPrice()))
                .toList();
        given(productRepository.findByCategoryOrderByPriceAsc("전자제품"))
                .willReturn(electronicsAndSorted);

        // when
        List<ProductResponse> responses = getProductsUseCase.execute("전자제품", "price");

        // then
        assertThat(responses).hasSize(2);
        assertThat(responses).allMatch(r -> r.category().equals("전자제품"));
        assertThat(responses.get(0).price()).isLessThan(responses.get(1).price());
        assertThat(responses.get(0).name()).isEqualTo("무선 마우스"); // 35,000
        assertThat(responses.get(1).name()).isEqualTo("노트북");    // 1,500,000

        verify(productRepository, times(1)).findByCategoryOrderByPriceAsc("전자제품");
        verify(productRepository, never()).findAll();
        verify(productRepository, never()).findByCategory(anyString());
    }

    @Test
    @DisplayName("유효하지 않은 정렬 옵션은 무시하고 전체 조회한다")
    void 유효하지_않은_정렬_옵션() {
        // given
        given(productRepository.findAll()).willReturn(testProducts);

        // when
        List<ProductResponse> responses = getProductsUseCase.execute(null, "invalid");

        // then
        assertThat(responses).hasSize(3);
        verify(productRepository, times(1)).findAll();
        verify(productRepository, never()).findAllByOrderByPriceAsc();
    }

    @Test
    @DisplayName("빈 카테고리 값은 무시하고 전체 조회한다")
    void 빈_카테고리_값() {
        // given
        given(productRepository.findAll()).willReturn(testProducts);

        // when
        List<ProductResponse> responses = getProductsUseCase.execute("", null);

        // then
        assertThat(responses).hasSize(3);
        verify(productRepository, times(1)).findAll();
        verify(productRepository, never()).findByCategory(anyString());
    }

    @Test
    @DisplayName("조회 결과가 없으면 빈 리스트를 반환한다")
    void 조회_결과_없음() {
        // given
        given(productRepository.findByCategory("존재하지않는카테고리"))
                .willReturn(List.of());

        // when
        List<ProductResponse> responses = getProductsUseCase.execute("존재하지않는카테고리", null);

        // then
        assertThat(responses).isEmpty();
        verify(productRepository, times(1)).findByCategory("존재하지않는카테고리");
    }
}
