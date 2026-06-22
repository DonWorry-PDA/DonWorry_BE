package com.sol.user.holding.repository;

import com.sol.user.holding.dto.HoldingWithProduct;
import com.sol.user.holding.entity.Holding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface HoldingRepository extends JpaRepository<Holding, Long> {
    @Query(value = """
            SELECT h.holding_id        AS holdingId,
                   fp.product_name     AS productName,
                   fp.product_type     AS productType,
                   h.evaluation_amount AS evaluationAmount
            FROM holding h
            JOIN financial_product fp ON h.product_id = fp.product_id
            JOIN account a ON h.account_id = a.account_id
            WHERE a.user_id = :userId
              AND a.account_type IN (:accountTypes)
            """, nativeQuery = true)
    List<HoldingWithProduct> findByUserIdAndAccountTypes(
            @Param("userId") Long userId,
            @Param("accountTypes") List<String> accountTypes
    );

}
