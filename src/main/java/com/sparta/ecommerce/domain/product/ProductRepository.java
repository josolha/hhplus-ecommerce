package com.sparta.ecommerce.domain.product;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    Optional<Product> findById(String id);
    List<Product> findAll();
    List<Product> findByCategory(String category);
    List<Product> findAllByOrderByPriceAsc();
    List<Product> findByCategoryOrderByPriceAsc(String category);
    void save(Product product);
}
