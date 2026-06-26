package com.sol.user.asset.service;

import com.sol.user.account.entity.Account;
import com.sol.user.account.repository.AccountRepository;
import com.sol.user.asset.dto.AssetBreakdown;
import com.sol.user.holding.dto.HoldingWithProduct;
import com.sol.user.holding.repository.HoldingRepository;
import com.sol.user.portfolio.infra.rest.ProductBatchClient;
import com.sol.user.portfolio.infra.rest.ProductBatchItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 자산 집계 단일 진실원천(예수금만 계약). 계좌 예수금 + 보유종목 평가액을 한 곳에서 분해한다.
 *
 * <p>예전엔 플랜·은퇴시뮬·허브가 각자 {@code Σ deposit_balance}를 손으로 합산해, "deposit_balance가
 * 총액이냐 예수금이냐"가 모듈마다 어긋났다. 마이데이터 계약은 {@code deposit_balance = 예수금(현금)만},
 * 종목값은 holding.evaluationAmount에만 존재하므로 모든 합산은 더하기다. 분류 기준은 선택 UI(#115)와
 * 동일하게 STOCK만 제외(ETF·FUND·BOND는 월급 재료에 포함).
 */
@Component
@RequiredArgsConstructor
public class AssetAggregator {

    /** 55세 인출제약이 걸린 연금 계좌. */
    private static final Set<String> PENSION_ACCOUNT_TYPES = Set.of("PENSION_SAVING", "IRP");
    /** 월급 재료에서 제외하는 종목 유형 — 청산해야 수익이 나는 개별주식. */
    private static final String STOCK_PRODUCT_TYPE = "STOCK";

    private final AccountRepository accountRepository;
    private final HoldingRepository holdingRepository;
    private final ProductBatchClient productBatchClient;

    @Transactional(readOnly = true)
    public AssetBreakdown aggregate(Long userId) {
        return aggregateSnapshot(userId).breakdown();
    }

    /**
     * 월급 만들기 전용 집계 — 사용자가 선택UI(#115)에서 제외한 계좌·보유종목을 빼고 집계한다.
     * 순자산·투자건강검진·은퇴시뮬은 전체 자산을 봐야 하므로 제외를 적용하지 않는 {@link #aggregate(Long)}을 쓴다.
     * 제외 입도는 계좌(accountId)·종목(holdingId) 독립 — 계좌를 빼도 그 계좌의 보유종목은 별도 토글이라 유지된다.
     */
    @Transactional(readOnly = true)
    public AssetBreakdown aggregate(Long userId, Set<Long> excludedAccountIds, Set<Long> excludedHoldingIds) {
        return aggregateSnapshot(userId, excludedAccountIds, excludedHoldingIds).breakdown();
    }

    /**
     * {@link #aggregate}와 동일한 계산이지만, 호출부가 이미 읽은 accounts/holdings/products를
     * 재사용할 수 있도록 원본 데이터까지 함께 반환한다. 개별주 종목명처럼 {@link AssetBreakdown}이
     * 제공하지 않는 분해가 필요한 소비처(투자 건강검진 등)는 이 메서드로 DB·외부호출 중복을 피한다.
     */
    @Transactional(readOnly = true)
    public AssetSnapshot aggregateSnapshot(Long userId) {
        return aggregateSnapshot(userId, Set.of(), Set.of());
    }

    @Transactional(readOnly = true)
    public AssetSnapshot aggregateSnapshot(Long userId, Set<Long> excludedAccountIds, Set<Long> excludedHoldingIds) {
        List<Account> accounts = accountRepository.findByUserUserId(userId).stream()
                .filter(account -> !excludedAccountIds.contains(account.getAccountId()))
                .toList();
        BigDecimal cash = accounts.stream()
                .map(account -> nz(account.getDepositBalance()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pensionCash = accounts.stream()
                .filter(account -> PENSION_ACCOUNT_TYPES.contains(account.getAccountType()))
                .map(account -> nz(account.getDepositBalance()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<HoldingWithProduct> holdings = holdingRepository.findHoldingsWithAccountTypeByUserId(userId).stream()
                .filter(holding -> !excludedHoldingIds.contains(holding.getHoldingId()))
                .toList();
        if (holdings.isEmpty()) {
            // 보유종목이 없으면 product-module 조회 없이 예수금만으로 확정.
            AssetBreakdown breakdown =
                    new AssetBreakdown(cash, pensionCash, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
            return new AssetSnapshot(breakdown, accounts, holdings, Map.of());
        }

        Map<Long, ProductBatchItem> products = productBatchClient.fetchProducts(
                holdings.stream().map(HoldingWithProduct::getProductId).toList());

        BigDecimal nonStock = BigDecimal.ZERO;
        BigDecimal pensionHolding = BigDecimal.ZERO;
        BigDecimal stock = BigDecimal.ZERO;
        for (HoldingWithProduct holding : holdings) {
            BigDecimal eval = nz(holding.getEvaluationAmount());
            if (isStock(products.get(holding.getProductId()))) {
                stock = stock.add(eval);
            } else if (PENSION_ACCOUNT_TYPES.contains(holding.getAccountType())) {
                // 연금계좌(IRP·연금저축)의 비STOCK 종목 — 55세 제약이라 즉시가용에서 빠지도록 별도 집계.
                pensionHolding = pensionHolding.add(eval);
            } else {
                nonStock = nonStock.add(eval);
            }
        }
        AssetBreakdown breakdown = new AssetBreakdown(cash, pensionCash, nonStock, pensionHolding, stock);
        return new AssetSnapshot(breakdown, accounts, holdings, products);
    }

    /** {@link #aggregate} 산출의 원본 데이터(accounts/holdings/products)까지 포함한 스냅샷. */
    public record AssetSnapshot(
            AssetBreakdown breakdown,
            List<Account> accounts,
            List<HoldingWithProduct> holdings,
            Map<Long, ProductBatchItem> products
    ) {
    }

    private boolean isStock(ProductBatchItem product) {
        return product != null && STOCK_PRODUCT_TYPE.equals(product.productType());
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
