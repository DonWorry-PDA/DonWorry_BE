package com.sol.user.asset.service;

import com.sol.user.asset.dto.AssetBreakdown;
import com.sol.user.asset.dto.AssetBreakdown.AssetRole;
import com.sol.user.asset.dto.InvestmentCheckResponse;
import com.sol.user.asset.dto.InvestmentCheckResponse.GrowthAsset;
import com.sol.user.asset.dto.InvestmentCheckResponse.RoleContribution;
import com.sol.user.holding.dto.HoldingWithProduct;
import com.sol.user.portfolio.config.PortfolioConstants;
import com.sol.user.portfolio.infra.rest.ProductBatchItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 투자 건강검진(#2) 상세 산출. 자산을 4역할(현금흐름·성장·잠자는 돈·연금)로 분해하고, 개별주 성장 블록을 만든다.
 *
 * <p>금액 단일 진실원천은 {@link AssetAggregator}({@link AssetBreakdown})다. 역할 4분류는 그 5개 구성을
 * 합이 grossTotal과 일치하도록 재배열한 것이라 별도 합산을 하지 않는다. 개별주의 종목별 분해(쏠림·종목명)는
 * AssetBreakdown이 제공하지 않으므로 {@link AssetAggregator#aggregateSnapshot}이 함께 반환한
 * holdings+products에서 STOCK만 집계한다(개별주 보유 시에만, DB·외부호출 중복 없음).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InvestmentCheckService {

    private static final String STOCK_PRODUCT_TYPE = "STOCK";
    private static final int MONTHS_PER_YEAR = 12;

    // 종목 쏠림 임계값 — 최대 단일종목 비중(%) 기준. (분산투자 통념: 단일종목 40% 미만 양호)
    private static final int CONCENTRATION_LOW_MAX = 40;   // < 40 → 낮음
    private static final int CONCENTRATION_MID_MAX = 70;   // < 70 → 보통, 이상 → 높음

    /**
     * 성장 자산 정성 멘트 — 숫자·현재배당 단정 없이 성장→현금흐름 재배치만 유도.
     * (종목 배당률 미적재 + 종목별 편차 큼 → "월 N원" 숫자 약속은 고배당 종목에서 오조언.)
     */
    private static final String GROWTH_SUGGESTION =
            "자본차익을 노리는 성장 자산이에요. 일부를 배당 중심 자산으로 옮기면 매달 들어오는 현금흐름을 만들 수 있어요.";

    private final AssetAggregator assetAggregator;

    public InvestmentCheckResponse check(Long userId) {
        AssetAggregator.AssetSnapshot snapshot = assetAggregator.aggregateSnapshot(userId);
        AssetBreakdown breakdown = snapshot.breakdown();

        Integer cashflowRatio = breakdown.cashflowAssetRatio();
        return InvestmentCheckResponse.builder()
                // 헤드라인은 도넛 조각(CASHFLOW 역할 ratio)과 동일 출처라 항상 일치한다. 자산 없으면 0.
                .cashflowAssetRatio(cashflowRatio == null ? 0 : cashflowRatio)
                .totalAsset(breakdown.grossTotal())
                .roles(buildRoles(breakdown))
                .growthAsset(buildGrowthAsset(snapshot, breakdown))
                .build();
    }

    /**
     * 4역할 분해. 금액·비율은 {@link AssetBreakdown#roleAllocation()}(단일 출처)에서 그대로 받고,
     * 라벨·설명·월 현금흐름 추정만 표시 계층인 이곳에서 붙인다.
     */
    private List<RoleContribution> buildRoles(AssetBreakdown breakdown) {
        return breakdown.roleAllocation().stream()
                .map(slice -> RoleContribution.builder()
                        .role(slice.role().name())
                        .label(label(slice.role()))
                        .amount(slice.amount())
                        .ratio(slice.ratio())
                        .monthlyCashflow(slice.role() == AssetRole.CASHFLOW
                                ? monthlyDividend(slice.amount())
                                : BigDecimal.ZERO)
                        .note(note(slice.role()))
                        .build())
                .toList();
    }

    private String label(AssetRole role) {
        return switch (role) {
            case CASHFLOW -> "현금흐름";
            case GROWTH -> "성장";
            case IDLE -> "잠자는 돈";
            case PENSION -> "연금";
        };
    }

    private String note(AssetRole role) {
        return switch (role) {
            case CASHFLOW -> "매달 배당·이자가 들어오는 돈";
            case GROWTH -> "자본차익을 노리는 돈 (월급은 아직 만들지 않아요)";
            case IDLE -> "아직 일하지 않고 쉬고 있는 현금";
            case PENSION -> "55세까지 묶인 노후 자금";
        };
    }

    /** 개별주 성장 블록. 개별주 보유가 없으면 null. */
    private GrowthAsset buildGrowthAsset(AssetAggregator.AssetSnapshot snapshot, AssetBreakdown breakdown) {
        BigDecimal stockTotal = breakdown.stockHoldingValue();
        if (stockTotal.signum() <= 0) {
            return null;
        }

        Map<String, BigDecimal> byStock = aggregateStocks(snapshot);
        Map.Entry<String, BigDecimal> top = byStock.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElse(null);

        int concentration = top == null ? 0 : percent(top.getValue(), stockTotal);

        return GrowthAsset.builder()
                .amount(stockTotal)
                .topStockName(top == null ? null : top.getKey())
                .concentrationRatio(concentration)
                .concentrationLevel(concentrationLevel(concentration))
                .suggestion(GROWTH_SUGGESTION)
                .build();
    }

    /** STOCK 보유만 종목명 기준으로 평가액 집계. (AssetBreakdown은 종목별 분해를 제공하지 않음.) */
    private Map<String, BigDecimal> aggregateStocks(AssetAggregator.AssetSnapshot snapshot) {
        List<HoldingWithProduct> holdings = snapshot.holdings();
        if (holdings.isEmpty()) {
            return Map.of();
        }
        Map<Long, ProductBatchItem> products = snapshot.products();

        Map<String, BigDecimal> byStock = new LinkedHashMap<>();
        for (HoldingWithProduct holding : holdings) {
            ProductBatchItem product = products.get(holding.getProductId());
            if (product == null || !STOCK_PRODUCT_TYPE.equals(product.productType())) {
                continue;
            }
            BigDecimal eval = nz(holding.getEvaluationAmount());
            if (eval.signum() == 0) {
                continue;
            }
            byStock.merge(product.productName(), eval, BigDecimal::add);
        }
        return byStock;
    }

    private String concentrationLevel(int concentration) {
        if (concentration < CONCENTRATION_LOW_MAX) {
            return "낮음";
        }
        if (concentration < CONCENTRATION_MID_MAX) {
            return "보통";
        }
        return "높음";
    }

    /** 연 대표배당률 기준 월 현금흐름 추정액(원, 정수). */
    private BigDecimal monthlyDividend(BigDecimal amount) {
        return amount.multiply(PortfolioConstants.REPRESENTATIVE_DIVIDEND_RATE)
                .divide(BigDecimal.valueOf(MONTHS_PER_YEAR), 0, RoundingMode.HALF_UP);
    }

    /** numerator/denominator 정수 %. denominator 0 이면 0. */
    private int percent(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.signum() == 0) {
            return 0;
        }
        return nz(numerator).multiply(BigDecimal.valueOf(100))
                .divide(denominator, 0, RoundingMode.HALF_UP)
                .intValue();
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
