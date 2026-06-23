package com.sol.product.pensionsaving.repository;

import com.sol.product.pensionsaving.entity.PensionSavingDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PensionSavingDetailRepository extends JpaRepository<PensionSavingDetail, Long> {

    Optional<PensionSavingDetail> findByProductId(Long productId);
}
