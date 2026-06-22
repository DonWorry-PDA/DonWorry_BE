package com.sol.user.holding.repository;

import com.sol.user.holding.dto.HoldingWithProduct;
import com.sol.user.holding.entity.Holding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
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

    // 보유 ETF의 월 환산 분배금 합계.
    // 분배금 = 1회 지급액(amount_per_unit) × 보유수량 ÷ 분배주기(개월). 월=÷1, 분기=÷3, 연=÷12.
    // 상품별 최신 지급분(payment_date 최대) 1건만 사용해 다회 이력 중복합산을 방지.
    @Query(value = """
            SELECT COALESCE(SUM(d.amount_per_unit * h.quantity / e.distribution_interval_months), 0)
            FROM holding h
            JOIN account a       ON h.account_id = a.account_id
            JOIN etf_detail e    ON e.product_id = h.product_id
            JOIN dividend_history d ON d.product_id = h.product_id
                 AND d.payment_date = (
                     SELECT MAX(d2.payment_date)
                     FROM dividend_history d2
                     WHERE d2.product_id = h.product_id
                 )
            WHERE a.user_id = :userId
              AND e.distribution_interval_months > 0
            """, nativeQuery = true)
    BigDecimal sumMonthlyDividendByUserId(@Param("userId") Long userId);

}
