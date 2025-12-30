package com.sparta.ecommerce.domain.product.repository;

import com.sparta.ecommerce.domain.product.entity.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 상품 저장소 인터페이스
 * JpaRepository가 기본 CRUD 메서드 제공 (findById, findAll, save 등)
 */
public interface ProductRepository extends JpaRepository<Product, String> {

    /**
     * 상품 조회 (비관적 락)
     * SELECT FOR UPDATE로 동시성 제어
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.productId = :productId")
    Optional<Product> findByIdWithLock(@Param("productId") String productId);

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

    /**
     * 재고 차감 (직접 UPDATE 쿼리)
     *
     * DB 레벨 재고 검증 포함:
     * - 재고가 충분할 때만 UPDATE 실행
     * - 재고 부족 시 affected rows = 0 반환
     * - 동시성 환경에서 재고 음수 방지
     *
     * @param productId 상품 ID
     * @param amount 차감할 수량
     * @return 업데이트된 행 수 (1: 성공, 0: 재고 부족)
     */
    @Modifying
    @Query("UPDATE Product p SET p.stock.quantity = p.stock.quantity - :amount WHERE p.productId = :productId AND p.stock.quantity >= :amount")
    int decreaseStock(@Param("productId") String productId, @Param("amount") int amount);
}
