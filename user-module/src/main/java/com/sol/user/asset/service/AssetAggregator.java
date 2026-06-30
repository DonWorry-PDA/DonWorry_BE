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
import java.math.RoundingMode;
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
 *
 * <p><b>화이트리스트 필터를 일부러 안 거는 이유(#141 정합):</b> #141 확정표는 "화이트리스트 ETF 26종만
 * 재료, off-whitelist ETF(타사 TIGER·KODEX 등)는 운용대상 외"였다. 그러나 현재 보유 ETF는 시드가 SOL
 * (화이트리스트) 풀로만 구성돼({@code AssetMockService.saveHoldings}가 풀 밖 티커를 throw) off-whitelist
 * ETF가 데이터상 존재하지 않으므로 "STOCK만 제외" ≡ "화이트리스트 26종"이라 결과가 동일하다. 즉 여기
 * 화이트리스트 ticker 대조를 추가해도 현 데이터에선 no-op다 — 실 마이데이터로 타사 ETF가 유입되는 시점에
 * 풀 확대 정책과 함께 후속으로 다룬다(섣불리 걸면 타사 ETF 보유자의 월급 재료가 사라짐). 이 STOCK-only를
 * "버그"로 보고 화이트리스트 필터를 도로 걸지 말 것.
 */
@Component
@RequiredArgsConstructor
public class AssetAggregator {

    /** 55세 인출제약이 걸린 연금 계좌. */
    private static final Set<String> PENSION_ACCOUNT_TYPES = Set.of("PENSION_SAVING", "IRP");
    /** 약정이 걸린 정기예금 계좌 — 순자산·floor 모수엔 포함하되 매수 실탄에서 제외(pinnedSafe carve-out). */
    private static final String DEPOSIT_ACCOUNT_TYPE = "DEPOSIT";
    /** 월급 재료에서 제외하는 종목 유형 — 청산해야 수익이 나는 개별주식. */
    private static final String STOCK_PRODUCT_TYPE = "STOCK";
    private static final String ETF_PRODUCT_TYPE = "ETF";

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
        // DEPOSIT(정기예금) 잔고는 약정이 걸린 pinnedSafe — 자유현금(cash)과 분리해 매수 실탄에서 제외.
        BigDecimal cash = accounts.stream()
                .filter(account -> !DEPOSIT_ACCOUNT_TYPE.equals(account.getAccountType()))
                .map(account -> nz(account.getDepositBalance()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pensionCash = accounts.stream()
                .filter(account -> PENSION_ACCOUNT_TYPES.contains(account.getAccountType()))
                .map(account -> nz(account.getDepositBalance()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pinnedSafe = accounts.stream()
                .filter(account -> DEPOSIT_ACCOUNT_TYPE.equals(account.getAccountType()))
                .map(account -> nz(account.getDepositBalance()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<HoldingWithProduct> holdings = holdingRepository.findHoldingsWithAccountTypeByUserId(userId).stream()
                .filter(holding -> !excludedHoldingIds.contains(holding.getHoldingId()))
                .toList();
        if (holdings.isEmpty()) {
            // 보유종목이 없으면 product-module 조회 없이 예수금만으로 확정.
            AssetBreakdown breakdown =
                    new AssetBreakdown(cash, pensionCash, pinnedSafe, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
            return new AssetSnapshot(breakdown, accounts, holdings, Map.of());
        }

        Map<Long, ProductBatchItem> products = productBatchClient.fetchProducts(
                holdings.stream().map(HoldingWithProduct::getProductId).toList());

        // STOCK 보유의 현재가를 product-module에서 배치 조회 (Redis 우선, daily_price 폴백)
        List<Long> stockProductIds = holdings.stream()
                .filter(h -> isStock(products.get(h.getProductId())))
                .map(HoldingWithProduct::getProductId)
                .distinct()
                .toList();
        Map<Long, Long> stockPrices = stockProductIds.isEmpty()
                ? Map.of() : productBatchClient.fetchStockPrices(stockProductIds);
        if (stockPrices == null) {
            stockPrices = Map.of();
        }

        List<Long> etfProductIds = holdings.stream()
                .filter(h -> isEtf(products.get(h.getProductId())))
                .map(HoldingWithProduct::getProductId)
                .distinct()
                .toList();
        Map<Long, Long> etfPrices = etfProductIds.isEmpty()
                ? Map.of() : productBatchClient.fetchEtfPrices(etfProductIds);
        if (etfPrices == null) {
            etfPrices = Map.of();
        }

        BigDecimal nonStock = BigDecimal.ZERO;
        BigDecimal pensionHolding = BigDecimal.ZERO;
        BigDecimal stock = BigDecimal.ZERO;
        for (HoldingWithProduct holding : holdings) {
            if (isStock(products.get(holding.getProductId()))) {
                // 평가액 = 수량 × 실시간 현재가 (DB 저장 evaluationAmount 미사용)
                long price = stockPrices.getOrDefault(holding.getProductId(), 0L);
                BigDecimal qty = holding.getQuantity() != null ? holding.getQuantity() : BigDecimal.ZERO;
                stock = stock.add(qty.multiply(BigDecimal.valueOf(price)));
            } else if (PENSION_ACCOUNT_TYPES.contains(holding.getAccountType())) {
                // 연금계좌(IRP·연금저축)의 비STOCK 종목 — 55세 제약이라 즉시가용에서 빠지도록 별도 집계.
                pensionHolding = pensionHolding.add(currentEtfValuationOrStored(holding, products, etfPrices));
            } else {
                nonStock = nonStock.add(currentEtfValuationOrStored(holding, products, etfPrices));
            }
        }
        AssetBreakdown breakdown = new AssetBreakdown(cash, pensionCash, pinnedSafe, nonStock, pensionHolding, stock);
        return new AssetSnapshot(breakdown, accounts, holdings, products, stockPrices, etfPrices);
    }

    /**
     * {@link #aggregate} 산출의 원본 데이터까지 포함한 스냅샷.
     *
     * <p>{@code stockPrices}(productId→현재가)는 개별주 평가의 단일 출처다. 개별주는
     * {@code holding.evaluation_amount}가 null이라 표시용 매퍼가 evaluationAmount를 읽으면 0이 된다 —
     * 총자산(grossTotal)은 여기 실시간가로 평가하므로, 화면 표시도 반드시 이 맵으로 수량×현재가를 써야
     * "총자산엔 주식 포함, 표시엔 0"인 불일치(#305)가 안 생긴다.
     */
    public record AssetSnapshot(
            AssetBreakdown breakdown,
            List<Account> accounts,
            List<HoldingWithProduct> holdings,
            Map<Long, ProductBatchItem> products,
            Map<Long, Long> stockPrices,
            Map<Long, Long> etfPrices
    ) {
        /** stockPrices 없는 하위호환 생성자 — 보유종목 없음/주식 없음 경로 및 테스트용(빈 맵). */
        public AssetSnapshot(AssetBreakdown breakdown, List<Account> accounts,
                             List<HoldingWithProduct> holdings, Map<Long, ProductBatchItem> products) {
            this(breakdown, accounts, holdings, products, Map.of(), Map.of());
        }

        public AssetSnapshot(AssetBreakdown breakdown, List<Account> accounts,
                             List<HoldingWithProduct> holdings, Map<Long, ProductBatchItem> products,
                             Map<Long, Long> stockPrices) {
            this(breakdown, accounts, holdings, products, stockPrices, Map.of());
        }
    }

    private boolean isStock(ProductBatchItem product) {
        return product != null && STOCK_PRODUCT_TYPE.equals(product.productType());
    }

    private boolean isEtf(ProductBatchItem product) {
        return product != null && ETF_PRODUCT_TYPE.equals(product.productType());
    }

    private BigDecimal currentEtfValuationOrStored(
            HoldingWithProduct holding,
            Map<Long, ProductBatchItem> products,
            Map<Long, Long> etfPrices
    ) {
        if (!isEtf(products.get(holding.getProductId()))) {
            return nz(holding.getEvaluationAmount());
        }
        long price = etfPrices.getOrDefault(holding.getProductId(), 0L);
        if (price <= 0 || holding.getQuantity() == null) {
            return nz(holding.getEvaluationAmount());
        }
        return holding.getQuantity()
                .multiply(BigDecimal.valueOf(price))
                .setScale(0, RoundingMode.HALF_UP);
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
