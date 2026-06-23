package com.sol.product.etf.repository;

import com.sol.product.etf.entity.EtfDetail;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EtfDetailRepository extends JpaRepository<EtfDetail, Long> {

    @EntityGraph(attributePaths = {"product"})
    List<EtfDetail> findAll();

    @EntityGraph(attributePaths = {"product"})
    List<EtfDetail> findAllByAssetManager(String assetManager);

    @EntityGraph(attributePaths = {"product"})
    List<EtfDetail> findAllByTickerCodeIn(List<String> tickerCodes);

    Optional<EtfDetail> findByTickerCode(String tickerCode);

    Optional<EtfDetail> findByProductProductId(Long productId);

    @EntityGraph(attributePaths = {"product"})
    Optional<EtfDetail> findWithProductByTickerCode(String tickerCode);

    @EntityGraph(attributePaths = {"product"})
    List<EtfDetail> findAllByProductProductIdIn(List<Long> productIds);
}
