package com.sol.product.bond.repository;

import com.sol.product.bond.entity.BondDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BondDetailRepository extends JpaRepository<BondDetail, Long> {

    Optional<BondDetail> findByProductProductId(Long productId);

    Optional<BondDetail> findByTickerCode(String tickerCode);
}
