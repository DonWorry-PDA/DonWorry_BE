package com.sol.product.dailyprice.repository;

import com.sol.product.dailyprice.entity.DailyPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyPriceRepository extends JpaRepository<DailyPrice, Long> {

    List<DailyPrice> findAllByProductProductId(Long productId);

    Optional<DailyPrice> findByProductProductIdAndPriceDate(Long productId, LocalDate priceDate);

    Optional<DailyPrice> findTopByProductProductIdOrderByPriceDateDesc(Long productId);

    void deleteAllByProductProductId(Long productId);

    @Query("SELECT d FROM DailyPrice d WHERE d.product.productId IN :productIds " +
           "AND d.priceDate = (SELECT MAX(d2.priceDate) FROM DailyPrice d2 WHERE d2.product.productId = d.product.productId)")
    List<DailyPrice> findLatestByProductIds(@Param("productIds") List<Long> productIds);
}