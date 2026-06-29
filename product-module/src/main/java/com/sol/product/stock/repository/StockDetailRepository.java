package com.sol.product.stock.repository;

import com.sol.product.stock.entity.StockDetail;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface StockDetailRepository extends JpaRepository<StockDetail, Long> {

    Optional<StockDetail> findByProductProductId(Long productId);

    Optional<StockDetail> findByTickerCode(String tickerCode);

    @EntityGraph(attributePaths = "product")
    List<StockDetail> findAllByTickerCodeIn(List<String> tickers);

    @EntityGraph(attributePaths = "product")
    List<StockDetail> findAllByProductProductIdIn(List<Long> productIds);

    @Query("SELECT s.tickerCode FROM StockDetail s WHERE s.tickerCode IS NOT NULL AND s.tickerCode <> ''")
    List<String> findAllTickerCodes();
}
