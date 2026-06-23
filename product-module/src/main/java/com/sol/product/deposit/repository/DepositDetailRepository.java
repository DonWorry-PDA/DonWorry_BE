package com.sol.product.deposit.repository;

import com.sol.product.deposit.entity.DepositDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DepositDetailRepository extends JpaRepository<DepositDetail, Long> {

    Optional<DepositDetail> findByProductProductId(Long productId);
}
