package com.sparta.ecommerce.domain.product.repository;

import com.sparta.ecommerce.domain.product.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 상품 저장소 인터페이스
 * JpaRepository가 기본 CRUD 메서드 제공 (findById, findAll, save 등)
 */
public interface ProductRepository extends JpaRepository<Product, String> {

    /**
     * 카테고리로 상품 조회
     */
    List<Product> findByCategory(String category);

    /**
     * 가격 오름차순으로 전체 상품 조회
     */
    List<Product> findAllByOrderByPriceAsc();

    /**
     * 카테고리별 가격 오름차순 조회
     */
    List<Product> findByCategoryOrderByPriceAsc(String category);

    /**
     * 여러 상품 ID로 조회
     * JpaRepository의 findAllById 사용 권장, 또는 커스텀 메서드
     */
    List<Product> findByProductIdIn(List<String> productIds);
}
