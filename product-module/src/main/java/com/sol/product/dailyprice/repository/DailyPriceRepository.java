package com.sol.product.dailyprice.repository;

import com.sol.product.dailyprice.entity.DailyPrice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyPriceRepository extends JpaRepository<DailyPrice, Long> {

    List<DailyPrice> findAllByProductProductId(Long productId);

    Optional<DailyPrice> findByProductProductIdAndPriceDate(Long productId, LocalDate priceDate);

    Optional<DailyPrice> findTopByProductProductIdOrderByPriceDateDesc(Long productId);

    void deleteAllByProductProductId(Long productId);
}