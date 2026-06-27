package com.sol.user.holding.repository;

import com.sol.user.account.entity.Account;
import com.sol.user.holding.dto.EtfHolding;
import com.sol.user.holding.dto.HoldingWithProduct;
import com.sol.user.holding.dto.HoldingDividendCalendarProjection;
import com.sol.user.holding.dto.HoldingDividendPaymentProjection;
import com.sol.user.holding.dto.HoldingWithQuantityAndType;
import com.sol.user.holding.dto.PensionHoldingProjection;
import com.sol.user.holding.dto.StockDividendProjection;
import com.sol.user.holding.dto.StockTickerProductId;
import com.sol.user.holding.entity.Holding;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface HoldingRepository extends JpaRepository<Holding, Long> {

    List<Holding> findByAccountIn(List<Account> accounts);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Holding h WHERE h.account.accountId IN :accountIds")
    void deleteAllByAccountIdIn(@Param("accountIds") List<Long> accountIds);

    Optional<Holding> findByAccountAccountIdAndProductId(Long accountId, Long productId);

    @EntityGraph(attributePaths = "account")
    List<Holding> findByAccountUserUserIdOrderByHoldingIdAsc(Long userId);

    @Query(value = """
            SELECT h.holding_id        AS holdingId,
                   a.account_id        AS accountId,
                   h.product_id        AS productId,
                   h.evaluation_amount AS evaluationAmount,
                   a.account_type      AS accountType
            FROM holding h
            JOIN account a ON h.account_id = a.account_id
            WHERE a.user_id = :userId
              AND a.account_type IN (:accountTypes)
            """, nativeQuery = true)
    List<HoldingWithProduct> findByUserIdAndAccountTypes(
            @Param("userId") Long userId,
            @Param("accountTypes") List<String> accountTypes
    );

    @Query(value = """
            SELECT h.holding_id        AS holdingId,
                   a.account_id        AS accountId,
                   h.product_id        AS productId,
                   h.evaluation_amount AS evaluationAmount,
                   a.account_type      AS accountType
            FROM holding h
            JOIN account a ON h.account_id = a.account_id
            WHERE a.user_id = :userId
            """, nativeQuery = true)
    List<HoldingWithProduct> findHoldingsWithAccountTypeByUserId(@Param("userId") Long userId);

    // 사용자의 전체 보유 종목 (holding_id, product_id, quantity) — product-module REST로 ETF 여부 판별 후 월 분배금 계산에 사용.
    // holding_id는 월급 제외목록(HOLDING_*) 필터에 사용.
    @Query(value = """
            SELECT h.holding_id AS holdingId,
                   h.product_id AS productId,
                   h.quantity   AS quantity
            FROM holding h
            JOIN account a ON h.account_id = a.account_id
            WHERE a.user_id = :userId
            """, nativeQuery = true)
    List<EtfHolding> findAllHoldingsByUserId(@Param("userId") Long userId);

    // 개별주 mock 시드용: ticker_code → product_id (product_type='STOCK'만)
    @Query(value = """
            SELECT s.ticker_code AS ticker,
                   s.product_id  AS productId
            FROM stock_detail s
            JOIN financial_product fp ON fp.product_id = s.product_id
            WHERE fp.product_type = 'STOCK'
              AND s.ticker_code IN (:tickers)
            """, nativeQuery = true)
    List<StockTickerProductId> findStockProductIds(@Param("tickers") List<String> tickers);

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

    // 보유 ETF의 기간 내 실제 지급 분배금(확정). 캘린더 확정 분배금 표시(#219).
    // dividend_history의 실지급 행을 payment_date 기준으로 가져온다(투영과 달리 +interval 적용 안 함).
    // ⚠️ 한계: SUM(h.quantity)는 '현재' 보유 수량이라, 지급일 이후 해당 ETF를 추가 매수/매도하면
    //   단가×현재수량이 실제 지급액과 어긋날 수 있다(기존 예상 분배금 투영도 동일 전제).
    //   정확한 확정액은 '지급일 시점 보유수량 스냅샷' 또는 실입금 원천이 필요하나 현재 데이터엔 없다.
    //   데모 ETF 보유는 시드 고정(지급일 수량=현재 수량)이라 영향 없음. 데이터 확보 시 정밀화 대상.
    @Query(value = """
            SELECT h.product_id      AS productId,
                   fp.product_name   AS productName,
                   SUM(h.quantity)   AS quantity,
                   d.amount_per_unit AS amountPerUnit,
                   d.payment_date    AS paymentDate
            FROM holding h
            JOIN account a ON h.account_id = a.account_id
            JOIN financial_product fp ON fp.product_id = h.product_id
            JOIN etf_detail e ON e.product_id = h.product_id
            JOIN dividend_history d ON d.product_id = h.product_id
            WHERE a.user_id = :userId
              AND h.quantity IS NOT NULL
              AND d.amount_per_unit IS NOT NULL
              AND d.payment_date BETWEEN :from AND :to
            GROUP BY h.product_id, fp.product_name, d.amount_per_unit, d.payment_date
            ORDER BY d.payment_date, h.product_id
            """, nativeQuery = true)
    List<HoldingDividendPaymentProjection> findDividendPaymentsByUserId(
            @Param("userId") Long userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );

    // 투자 건강검진 성장블록용: 보유 개별주(STOCK)의 종목별 평가액 합 + 시가배당률
    @Query(value = """
            SELECT fp.product_id          AS productId,
                   fp.product_name        AS productName,
                   SUM(h.evaluation_amount) AS evaluationAmount,
                   s.dividend_yield       AS dividendYield,
                   s.sector               AS sector
            FROM holding h
            JOIN account a ON h.account_id = a.account_id
            JOIN financial_product fp ON fp.product_id = h.product_id
            JOIN stock_detail s ON s.product_id = h.product_id
            WHERE a.user_id = :userId
              AND fp.product_type = 'STOCK'
            GROUP BY fp.product_id, fp.product_name, s.dividend_yield, s.sector
            ORDER BY SUM(h.evaluation_amount) DESC
            """, nativeQuery = true)
    List<StockDividendProjection> findStockDividendsByUserId(@Param("userId") Long userId);

    @Query(value = """
            SELECT h.product_id    AS productId,
                   h.quantity      AS quantity,
                   a.account_type  AS accountType
            FROM holding h
            JOIN account a ON h.account_id = a.account_id
            WHERE a.user_id = :userId
            """, nativeQuery = true)
    List<HoldingWithQuantityAndType> findHoldingsWithQuantityAndTypeByUserId(@Param("userId") Long userId);

    @Query(value = """
            SELECT h.account_id        AS accountId,
                   h.evaluation_amount AS evaluationAmount
            FROM holding h
            JOIN account a ON h.account_id = a.account_id
            WHERE a.user_id = :userId
              AND a.account_type IN ('IRP', 'PENSION_SAVING')
            """, nativeQuery = true)
    List<PensionHoldingProjection> findPensionHoldingsByUserId(@Param("userId") Long userId);

    @Query(value = """
            SELECT COALESCE(SUM(
                CASE WHEN h.unrealized_gain_loss IS NOT NULL AND h.unrealized_gain_loss != ''
                     THEN CAST(h.unrealized_gain_loss AS SIGNED)
                     ELSE 0 END
            ), 0)
            FROM holding h
            JOIN account a ON h.account_id = a.account_id
            WHERE a.user_id = :userId
            """, nativeQuery = true)
    BigDecimal sumUnrealizedGainLossByUserId(@Param("userId") Long userId);
}
