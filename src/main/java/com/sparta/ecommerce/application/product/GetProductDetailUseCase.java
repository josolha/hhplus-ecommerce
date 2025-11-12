package com.sparta.ecommerce.application.product;

import com.sparta.ecommerce.application.product.dto.ProductResponse;
import com.sparta.ecommerce.domain.product.entity.Product;
import com.sparta.ecommerce.domain.product.repository.ProductRepository;
import com.sparta.ecommerce.domain.product.exception.ProductNotFoundException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class GetProductDetailUseCase {

    private final ProductRepository productRepository;

    public ProductResponse execute(String productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        return ProductResponse.from(product);
    }
}
