package com.sparta.ecommerce.application.product;

import com.sparta.ecommerce.application.product.dto.ProductResponse;
import com.sparta.ecommerce.domain.order.Order;
import com.sparta.ecommerce.domain.order.OrderItem;
import com.sparta.ecommerce.domain.order.OrderRepository;
import com.sparta.ecommerce.domain.order.OrderStatus;
import com.sparta.ecommerce.domain.product.Product;
import com.sparta.ecommerce.domain.product.ProductRepository;
import com.sparta.ecommerce.domain.product.vo.Stock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("인기 상품 조회 UseCase 테스트")
class GetPopularProductsUseCaseTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private GetPopularProductsUseCase getPopularProductsUseCase;

    private List<Order> testOrders;
    private List<Product> testProducts;

    @BeforeEach
    void setUp() {
        // 테스트 주문 데이터
        testOrders = List.of(
                Order.builder()
                        .orderId("O001")
                        .userId("U001")
                        .items(List.of(
                                OrderItem.builder().productId("P001").quantity(2).build(),
                                OrderItem.builder().productId("P002").quantity(1).build()
                        ))
                        .status(OrderStatus.COMPLETED)
                        .createdAt(LocalDateTime.now().minusDays(1))
                        .build(),
                Order.builder()
                        .orderId("O002")
                        .userId("U002")
                        .items(List.of(
                                OrderItem.builder().productId("P001").quantity(3).build(),
                                OrderItem.builder().productId("P003").quantity(2).build()
                        ))
                        .status(OrderStatus.COMPLETED)
                        .createdAt(LocalDateTime.now().minusDays(2))
                        .build(),
                Order.builder()
                        .orderId("O003")
                        .userId("U003")
                        .items(List.of(
                                OrderItem.builder().productId("P002").quantity(4).build()
                        ))
                        .status(OrderStatus.COMPLETED)
                        .createdAt(LocalDateTime.now().minusHours(5))
                        .build()
        );

        // 테스트 상품 데이터
        testProducts = List.of(
                Product.builder()
                        .productId("P001").name("노트북").price(1500000)
                        .stock(new Stock(10)).category("전자제품").build(),
                Product.builder()
                        .productId("P002").name("무선 마우스").price(35000)
                        .stock(new Stock(50)).category("전자제품").build(),
                Product.builder()
                        .productId("P003").name("키보드").price(49000)
                        .stock(new Stock(30)).category("전자제품").build()
        );
    }

    @Test
    @DisplayName("최근 3일간 판매량 기준 상위 5개 인기 상품을 조회한다")
    void 인기_상품_조회_성공() {
        // given
        given(orderRepository.findRecentOrders(3)).willReturn(testOrders);
        given(productRepository.findByIds(anyList()))
                .willReturn(List.of(testProducts.get(1), testProducts.get(0), testProducts.get(2)));

        // when
        List<ProductResponse> responses = getPopularProductsUseCase.execute(3, 5);

        // then
        assertThat(responses).hasSize(3);
        verify(orderRepository, times(1)).findRecentOrders(3);
        verify(productRepository, times(1)).findByIds(anyList());
    }

    @Test
    @DisplayName("판매량이 같은 경우 순서가 유지된다")
    void 동일_판매량_순서_유지() {
        // given
        List<Order> sameQuantityOrders = List.of(
                Order.builder()
                        .orderId("O001")
                        .userId("U001")
                        .items(List.of(
                                OrderItem.builder().productId("P001").quantity(3).build()
                        ))
                        .createdAt(LocalDateTime.now().minusDays(1))
                        .build(),
                Order.builder()
                        .orderId("O002")
                        .userId("U002")
                        .items(List.of(
                                OrderItem.builder().productId("P002").quantity(3).build()
                        ))
                        .createdAt(LocalDateTime.now().minusDays(2))
                        .build()
        );

        given(orderRepository.findRecentOrders(3)).willReturn(sameQuantityOrders);
        given(productRepository.findByIds(anyList()))
                .willReturn(List.of(testProducts.get(0), testProducts.get(1)));

        // when
        List<ProductResponse> responses = getPopularProductsUseCase.execute(3, 5);

        // then
        assertThat(responses).hasSize(2);
        verify(orderRepository, times(1)).findRecentOrders(3);
    }

    @Test
    @DisplayName("limit보다 적은 상품이 있을 경우 존재하는 상품만 반환한다")
    void limit보다_적은_상품() {
        // given
        given(orderRepository.findRecentOrders(3)).willReturn(testOrders);
        given(productRepository.findByIds(anyList())).willReturn(testProducts);

        // when
        List<ProductResponse> responses = getPopularProductsUseCase.execute(3, 10);

        // then
        assertThat(responses).hasSize(3);
        verify(orderRepository, times(1)).findRecentOrders(3);
    }

    @Test
    @DisplayName("최근 주문이 없으면 빈 리스트를 반환한다")
    void 주문_없음() {
        // given
        given(orderRepository.findRecentOrders(3)).willReturn(List.of());

        // when
        List<ProductResponse> responses = getPopularProductsUseCase.execute(3, 5);

        // then
        assertThat(responses).isEmpty();
        verify(orderRepository, times(1)).findRecentOrders(3);
        verify(productRepository, never()).findByIds(anyList());
    }

    @Test
    @DisplayName("조회 기간을 변경하여 인기 상품을 조회한다")
    void 조회_기간_변경() {
        // given
        given(orderRepository.findRecentOrders(7)).willReturn(testOrders);
        given(productRepository.findByIds(anyList())).willReturn(testProducts);

        // when
        List<ProductResponse> responses = getPopularProductsUseCase.execute(7, 5);

        // then
        assertThat(responses).isNotEmpty();
        verify(orderRepository, times(1)).findRecentOrders(7);
    }

    @Test
    @DisplayName("limit을 변경하여 상위 N개만 조회한다")
    void limit_변경() {
        // given
        given(orderRepository.findRecentOrders(3)).willReturn(testOrders);
        given(productRepository.findByIds(anyList()))
                .willReturn(List.of(testProducts.get(1), testProducts.get(0)));

        // when
        List<ProductResponse> responses = getPopularProductsUseCase.execute(3, 2);

        // then
        assertThat(responses).hasSize(2);
        verify(orderRepository, times(1)).findRecentOrders(3);
    }

    @Test
    @DisplayName("한 상품이 여러 주문에서 판매된 경우 판매량을 합산한다")
    void 판매량_합산() {
        // given
        List<Order> multipleOrders = List.of(
                Order.builder()
                        .orderId("O001")
                        .items(List.of(OrderItem.builder().productId("P001").quantity(2).build()))
                        .createdAt(LocalDateTime.now().minusDays(1))
                        .build(),
                Order.builder()
                        .orderId("O002")
                        .items(List.of(OrderItem.builder().productId("P001").quantity(3).build()))
                        .createdAt(LocalDateTime.now().minusHours(5))
                        .build(),
                Order.builder()
                        .orderId("O003")
                        .items(List.of(OrderItem.builder().productId("P001").quantity(1).build()))
                        .createdAt(LocalDateTime.now().minusHours(1))
                        .build()
        );

        given(orderRepository.findRecentOrders(3)).willReturn(multipleOrders);
        given(productRepository.findByIds(anyList()))
                .willReturn(List.of(testProducts.get(0)));

        // when
        List<ProductResponse> responses = getPopularProductsUseCase.execute(3, 5);

        // then
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).productId()).isEqualTo("P001");
        verify(orderRepository, times(1)).findRecentOrders(3);
    }
}
