package com.sparta.ecommerce.application.product;


import com.sparta.ecommerce.application.product.dto.ProductStockResponse;
import com.sparta.ecommerce.domain.product.Product;
import com.sparta.ecommerce.domain.product.ProductRepository;
import com.sparta.ecommerce.domain.product.exception.ProductNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetProductStockUseCase {

    private final ProductRepository productRepository;

    public ProductStockResponse execute(String productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        return ProductStockResponse.from(product);
    }
}
