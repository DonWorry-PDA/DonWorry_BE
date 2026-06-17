package com.sol.product.stock.repository;

import com.sol.product.stock.entity.StockDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StockDetailRepository extends JpaRepository<StockDetail, Long> {

    Optional<StockDetail> findByProductProductId(Long productId);

    Optional<StockDetail> findByTickerCode(String tickerCode);
}
