package com.sol.product.deposit.repository;

import com.sol.product.deposit.entity.DepositDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface DepositDetailRepository extends JpaRepository<DepositDetail, Long> {

    Optional<DepositDetail> findByProductProductId(Long productId);

    List<DepositDetail> findByProductProductIdIn(List<Long> productIds);

    @Query("SELECT d.product.productId FROM DepositDetail d")
    List<Long> findAllProductIds();
}
