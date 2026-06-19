package com.sol.product.dividend.repository;

import com.sol.product.dividend.entity.DividendHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DividendHistoryRepository extends JpaRepository<DividendHistory, Long> {

    Optional<DividendHistory> findByProductProductIdAndExDividendDate(Long productId, LocalDate exDividendDate);

    Optional<DividendHistory> findTopByProductProductIdOrderByPaymentDateDesc(Long productId);

    @Query("""
            SELECT d FROM DividendHistory d
            JOIN FETCH d.product p
            WHERE p.productId IN :productIds
              AND d.paymentDate = (
                  SELECT MAX(d2.paymentDate) FROM DividendHistory d2
                  WHERE d2.product.productId = p.productId
              )
            """)
    List<DividendHistory> findLatestByProductIds(@Param("productIds") List<Long> productIds);
}
