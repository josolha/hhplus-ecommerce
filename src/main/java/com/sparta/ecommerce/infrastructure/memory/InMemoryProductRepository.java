package com.sparta.ecommerce.infrastructure.memory;

import com.sparta.ecommerce.domain.product.Product;
import com.sparta.ecommerce.domain.product.ProductRepository;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;


@Repository
public class InMemoryProductRepository implements ProductRepository {

    private final Map<String, Product> store = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        save(Product.builder()
                .productId("P001").name("노트북").price(1500000).stock(10).category("전자제품").build());
        save(Product.builder()
                .productId("P002").name("무선 마우스").price(35000).stock(50).category("전자제품").build());
        save(Product.builder()
                .productId("P003").name("키보드").price(89000).stock(25).category("전자제품").build());
    }

    @Override
    public Optional<Product> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Product> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public void save(Product product) {
        store.put(product.getProductId(), product);
    }
}
