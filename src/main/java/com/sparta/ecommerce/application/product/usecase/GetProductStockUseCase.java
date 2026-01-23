package com.sparta.ecommerce.application.product.usecase;


import com.sparta.ecommerce.application.product.dto.ProductStockResponse;
import com.sparta.ecommerce.domain.product.entity.Product;
import com.sparta.ecommerce.domain.product.repository.ProductRepository;
import com.sparta.ecommerce.domain.product.exception.ProductNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetProductStockUseCase {

    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public ProductStockResponse execute(String productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        return ProductStockResponse.from(product);
    }
}
