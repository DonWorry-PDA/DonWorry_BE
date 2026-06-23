package com.sol.user.holding.repository;

import com.sol.user.holding.dto.EtfHolding;
import com.sol.user.holding.dto.HoldingWithProduct;
import com.sol.user.holding.dto.HoldingDividendCalendarProjection;
import com.sol.user.holding.entity.Holding;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface HoldingRepository extends JpaRepository<Holding, Long> {

    @EntityGraph(attributePaths = "account")
    List<Holding> findByAccountUserUserIdOrderByHoldingIdAsc(Long userId);

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

    @Query(value = """
            SELECT h.product_id                         AS productId,
                   fp.product_name                      AS productName,
                   SUM(h.quantity)                      AS quantity,
                   d.amount_per_unit                    AS amountPerUnit,
                   d.payment_date                       AS latestPaymentDate,
                   e.distribution_interval_months       AS distributionIntervalMonths
            FROM holding h
            JOIN account a ON h.account_id = a.account_id
            JOIN financial_product fp ON fp.product_id = h.product_id
            JOIN etf_detail e ON e.product_id = h.product_id
            JOIN dividend_history d ON d.dist_id = (
                     SELECT d2.dist_id
                     FROM dividend_history d2
                     WHERE d2.product_id = h.product_id
                       AND d2.payment_date IS NOT NULL
                     ORDER BY d2.payment_date DESC, d2.dist_id DESC
                     LIMIT 1
                 )
            WHERE a.user_id = :userId
              AND h.quantity IS NOT NULL
              AND e.distribution_interval_months > 0
            GROUP BY h.product_id,
                     fp.product_name,
                     d.amount_per_unit,
                     d.payment_date,
                     e.distribution_interval_months
            ORDER BY h.product_id
            """, nativeQuery = true)
    List<HoldingDividendCalendarProjection> findDividendCalendarInputsByUserId(
            @Param("userId") Long userId
    );
}
