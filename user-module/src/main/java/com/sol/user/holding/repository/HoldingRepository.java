package com.sol.user.holding.repository;

import com.sol.user.holding.dto.EtfHolding;
import com.sol.user.holding.dto.HoldingWithProduct;
import com.sol.user.holding.entity.Holding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface HoldingRepository extends JpaRepository<Holding, Long> {

    @Query(value = """
            SELECT h.holding_id        AS holdingId,
                   h.product_id        AS productId,
                   h.evaluation_amount AS evaluationAmount
            FROM holding h
            JOIN account a ON h.account_id = a.account_id
            WHERE a.user_id = :userId
              AND a.account_type IN (:accountTypes)
            """, nativeQuery = true)
    List<HoldingWithProduct> findByUserIdAndAccountTypes(
            @Param("userId") Long userId,
            @Param("accountTypes") List<String> accountTypes
    );

    // 사용자의 전체 보유 종목 (product_id, quantity) — product-module REST로 ETF 여부 판별 후 월 분배금 계산에 사용
    @Query(value = """
            SELECT h.product_id AS productId,
                   h.quantity   AS quantity
            FROM holding h
            JOIN account a ON h.account_id = a.account_id
            WHERE a.user_id = :userId
            """, nativeQuery = true)
    List<EtfHolding> findAllHoldingsByUserId(@Param("userId") Long userId);
}
