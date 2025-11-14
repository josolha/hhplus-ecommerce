package com.sparta.ecommerce.application.product;

import com.sparta.ecommerce.application.product.dto.ProductResponse;
import com.sparta.ecommerce.domain.product.entity.Product;
import com.sparta.ecommerce.domain.product.repository.ProductRepository;
import com.sparta.ecommerce.domain.product.ProductSortType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetProductsUseCase {

    private final ProductRepository productRepository;

    public List<ProductResponse> execute(String category, String sort) {
        // 1. Repository에서 조회
        List<Product> products = fetchProducts(category, sort);

        // 2. DTO 변환
        return products.stream()
                .map(ProductResponse::from)
                .toList();
    }

    /**
     * 카테고리와 정렬 조건에 맞는 상품 조회
     */
    private List<Product> fetchProducts(String category, String sort) {
        boolean hasCategory = hasCategory(category);
        ProductSortType sortType = ProductSortType.from(sort);

        // 카테고리 + 가격순 정렬
        if (hasCategory && sortType == ProductSortType.PRICE) {
            return productRepository.findByCategoryOrderByPriceAsc(category);
        }

        // 카테고리만
        if (hasCategory) {
            return productRepository.findByCategory(category);
        }

        // 가격순 정렬만
        if (sortType == ProductSortType.PRICE) {
            return productRepository.findAllByOrderByPriceAsc();
        }

        // 전체 조회
        return productRepository.findAll();
    }

    private boolean hasCategory(String category) {
        return category != null && !category.isBlank();
    }
}
