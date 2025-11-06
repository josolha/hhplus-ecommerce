package com.sparta.ecommerce.infrastructure.memory;

import com.sparta.ecommerce.domain.product.Product;
import com.sparta.ecommerce.domain.product.ProductRepository;
import com.sparta.ecommerce.domain.product.vo.Stock;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 인메모리 상품 저장소
 */
@Repository
@RequiredArgsConstructor
public class InMemoryProductRepository implements ProductRepository {

    private final InMemoryDataStore dataStore;

    @PostConstruct
    public void init() {
        save(Product.builder()
                .productId("P001").name("노트북").price(1500000).stock(new Stock(10)).category("전자제품").build());
        save(Product.builder()
                .productId("P002").name("무선 마우스").price(35000).stock(new Stock(70)).category("전자제품").build());
        save(Product.builder()
                .productId("P003").name("키보드").price(49000).stock(new Stock(60)).category("전자제품").build());
        save(Product.builder()
                .productId("P004").name("치킨").price(59000).stock(new Stock(50)).category("음식").build());
        save(Product.builder()
                .productId("P005").name("피자").price(69000).stock(new Stock(40)).category("음식").build());
        save(Product.builder()
                .productId("P006").name("조말론").price(79000).stock(new Stock(30)).category("뷰티").build());
        save(Product.builder()
                .productId("P007").name("킬리안").price(89000).stock(new Stock(0)).category("뷰티").build());
    }

    @Override
    public Optional<Product> findById(String id) {
        return Optional.ofNullable(dataStore.getProducts().get(id));
    }

    @Override
    public List<Product> findAll() {
        return new ArrayList<>(dataStore.getProducts().values());
    }

    @Override
    public List<Product> findByCategory(String category) {
        return dataStore.getProducts().values().stream()
                .filter(product -> category.equals(product.getCategory()))
                .toList();
    }

    @Override
    public List<Product> findAllByOrderByPriceAsc() {
        return dataStore.getProducts().values().stream()
                .sorted(Comparator.comparing(Product::getPrice))
                .toList();
    }

    @Override
    public List<Product> findByCategoryOrderByPriceAsc(String category) {
        return dataStore.getProducts().values().stream()
                .filter(product -> category.equals(product.getCategory()))
                .sorted(Comparator.comparing(Product::getPrice))
                .toList();
    }

    @Override
    public void save(Product product) {
        dataStore.getProducts().put(product.getProductId(), product);
    }
}
