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
        List<Account> accounts = accountRepository.findByUserUserId(userId);
        BigDecimal cash = accounts.stream()
                .map(account -> nz(account.getDepositBalance()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pensionCash = accounts.stream()
                .filter(account -> PENSION_ACCOUNT_TYPES.contains(account.getAccountType()))
                .map(account -> nz(account.getDepositBalance()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<HoldingWithProduct> holdings = holdingRepository.findHoldingsWithAccountTypeByUserId(userId);
        if (holdings.isEmpty()) {
            // 보유종목이 없으면 product-module 조회 없이 예수금만으로 확정.
            return new AssetBreakdown(cash, pensionCash, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
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
        return new AssetBreakdown(cash, pensionCash, nonStock, pensionHolding, stock);
    }

    private boolean isStock(ProductBatchItem product) {
        return product != null && STOCK_PRODUCT_TYPE.equals(product.productType());
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
