package com.sol.user.asset.service;

import com.sol.user.asset.dto.AssetBreakdown;
import com.sol.user.asset.dto.AssetBreakdown.AssetRole;
import com.sol.user.asset.dto.InvestmentCheckResponse;
import com.sol.user.asset.dto.InvestmentCheckResponse.GrowthAsset;
import com.sol.user.asset.dto.InvestmentCheckResponse.RoleContribution;
import com.sol.user.holding.dto.HoldingDividendCalendarProjection;
import com.sol.user.holding.dto.StockDividendProjection;
import com.sol.user.holding.repository.HoldingRepository;
import com.sol.user.portfolio.config.PortfolioConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

/**
 * 투자 건강검진(#2) 상세 산출. 자산을 4역할(현금흐름·성장·잠자는 돈·연금)로 분해하고, 개별주 성장 블록을 만든다.
 *
 * <p>금액 단일 진실원천은 {@link AssetAggregator}({@link AssetBreakdown})다. 역할 4분류·비율은
 * {@link AssetBreakdown#roleAllocation()}에서 그대로 받고, 여기서는 라벨·설명과 <b>실배당 기반 월 현금흐름</b>만 붙인다.
 *
 * <p>월 현금흐름은 추정치가 아니라 보유분 실데이터다: 현금흐름 역할은 보유 ETF의 실분배
 * ({@link HoldingRepository#findDividendCalendarInputsByUserId}), 성장 역할은 개별주의 종목별 시가배당률
 * ({@link HoldingRepository#findStockDividendsByUserId})로 산출한다. ETF 실분배 데이터가 전혀 없을 때만
 * 대표배당률({@link PortfolioConstants#REPRESENTATIVE_DIVIDEND_RATE})로 폴백한다(풀 밖 종목 방어).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InvestmentCheckService {

    private static final BigDecimal MONTHS_PER_YEAR = BigDecimal.valueOf(12);
    /** 연 시가배당률(%)을 월 배당액으로: eval × (yield/100) / 12 = eval × yield / 1200. */
    private static final BigDecimal PERCENT_MONTHS = BigDecimal.valueOf(1200);

    // 종목 쏠림 임계값 — 최대 단일종목 비중(%) 기준. (분산투자 통념: 단일종목 40% 미만 양호)
    private static final int CONCENTRATION_LOW_MAX = 40;   // < 40 → 낮음
    private static final int CONCENTRATION_MID_MAX = 70;   // < 70 → 보통, 이상 → 높음

    /** 성장 자산을 배당형으로 옮기면 현금흐름이 늘어나는 경우(저배당 성장주 위주). */
    private static final String SUGGESTION_MOVE =
            "성장에 베팅한 자산이에요. 일부를 배당 중심 자산으로 옮기면 매달 들어오는 현금흐름을 더 만들 수 있어요.";
    /** 이미 종목 배당이 배당형 ETF 수준 이상이라 옮기면 오히려 손해인 경우(고배당 가치주 위주). */
    private static final String SUGGESTION_KEEP =
            "이미 배당이 꾸준히 나오는 자산이에요. 배당 중심으로 옮기면 현금흐름이 오히려 줄 수 있어요.";

    private final AssetAggregator assetAggregator;
    private final HoldingRepository holdingRepository;

    public InvestmentCheckResponse check(Long userId) {
        AssetBreakdown breakdown = assetAggregator.aggregate(userId);

        List<StockDividendProjection> stocks = holdingRepository.findStockDividendsByUserId(userId);
        List<HoldingDividendCalendarProjection> etfDividends =
                holdingRepository.findDividendCalendarInputsByUserId(userId);

        BigDecimal cashflowMonthly = cashflowMonthlyDividend(breakdown, etfDividends);
        BigDecimal stockMonthly = stockMonthlyDividend(stocks);

        Integer cashflowRatio = breakdown.cashflowAssetRatio();
        return InvestmentCheckResponse.builder()
                // 헤드라인은 도넛 조각(CASHFLOW 역할 ratio)과 동일 출처라 항상 일치한다. 자산 없으면 0.
                .cashflowAssetRatio(cashflowRatio == null ? 0 : cashflowRatio)
                .totalAsset(breakdown.grossTotal())
                .roles(buildRoles(breakdown, cashflowMonthly, stockMonthly))
                .growthAsset(buildGrowthAsset(breakdown, stocks, stockMonthly))
                .build();
    }

    /**
     * 4역할 분해. 금액·비율은 {@link AssetBreakdown#roleAllocation()}(단일 출처)에서 그대로 받고,
     * 라벨·설명·월 현금흐름만 표시 계층인 이곳에서 붙인다. 월 현금흐름은 현금흐름=ETF 실분배, 성장=개별주 실배당,
     * 나머지(잠자는 돈·연금)는 0이다.
     */
    private List<RoleContribution> buildRoles(AssetBreakdown breakdown,
                                              BigDecimal cashflowMonthly, BigDecimal stockMonthly) {
        return breakdown.roleAllocation().stream()
                .map(slice -> RoleContribution.builder()
                        .role(slice.role().name())
                        .label(label(slice.role()))
                        .amount(slice.amount())
                        .ratio(slice.ratio())
                        .monthlyCashflow(switch (slice.role()) {
                            case CASHFLOW -> cashflowMonthly;
                            case GROWTH -> stockMonthly;
                            case IDLE, PENSION -> BigDecimal.ZERO;
                        })
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
            case GROWTH -> "자본차익을 노리는 돈 (배당이 나오면 함께 표시돼요)";
            case IDLE -> "아직 일하지 않고 쉬고 있는 현금";
            case PENSION -> "55세까지 묶인 노후 자금";
        };
    }

    /**
     * 개별주 성장 블록. 개별주 보유가 없으면 null. 현재 배당(종목 실배당) vs 배당형 ETF로 옮겼을 때의 배당을
     * 비교해, 옮기는 게 이득이면 이동 유도, 손해면 보유 유지 멘트로 분기한다.
     */
    private GrowthAsset buildGrowthAsset(AssetBreakdown breakdown,
                                         List<StockDividendProjection> stocks, BigDecimal stockMonthly) {
        BigDecimal stockTotal = breakdown.stockHoldingValue();
        if (stockTotal.signum() <= 0) {
            return null;
        }

        StockDividendProjection top = stocks.stream()
                .max(Comparator.comparing(s -> nz(s.getEvaluationAmount())))
                .orElse(null);
        int concentration = top == null ? 0 : percent(top.getEvaluationAmount(), stockTotal);

        // 전액 배당형 ETF로 옮겼을 때의 월 배당(대표배당률) vs 현재 종목 실배당.
        BigDecimal converted = monthlyByRate(stockTotal, PortfolioConstants.REPRESENTATIVE_DIVIDEND_RATE);
        BigDecimal delta = converted.subtract(stockMonthly);

        return GrowthAsset.builder()
                .amount(stockTotal)
                .topStockName(top == null ? null : top.getProductName())
                .concentrationRatio(concentration)
                .concentrationLevel(concentrationLevel(concentration))
                .currentMonthlyDividend(stockMonthly)
                .convertedMonthlyDividend(converted)
                .deltaMonthlyDividend(delta)
                .suggestion(delta.signum() > 0 ? SUGGESTION_MOVE : SUGGESTION_KEEP)
                .build();
    }

    /**
     * 현금흐름 역할의 월 배당 = 보유 ETF 실분배 합(종목별 수량 × 분배/주 ÷ 배당주기).
     * 실분배 데이터가 전혀 없으면(풀 밖 종목 등) 현금흐름 자산 전체를 대표배당률로 추정 폴백.
     */
    private BigDecimal cashflowMonthlyDividend(AssetBreakdown breakdown,
                                              List<HoldingDividendCalendarProjection> etfDividends) {
        if (etfDividends.isEmpty()) {
            return monthlyByRate(breakdown.nonStockHoldingValue(), PortfolioConstants.REPRESENTATIVE_DIVIDEND_RATE);
        }
        return etfDividends.stream()
                .map(this::monthlyFromDistribution)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** ETF 1종목 월 분배액 = 수량 × 분배/주 ÷ 배당주기(월). 주기는 쿼리에서 >0 보장. */
    private BigDecimal monthlyFromDistribution(HoldingDividendCalendarProjection p) {
        Integer interval = p.getDistributionIntervalMonths();
        if (interval == null || interval <= 0) {
            return BigDecimal.ZERO;
        }
        return nz(p.getQuantity()).multiply(nz(p.getAmountPerUnit()))
                .divide(BigDecimal.valueOf(interval), 0, RoundingMode.HALF_UP);
    }

    /** 성장 역할의 월 배당 = 개별주 종목별 (평가액 × 시가배당률 / 1200) 합. */
    private BigDecimal stockMonthlyDividend(List<StockDividendProjection> stocks) {
        return stocks.stream()
                .map(s -> nz(s.getEvaluationAmount()).multiply(nz(s.getDividendYield()))
                        .divide(PERCENT_MONTHS, 0, RoundingMode.HALF_UP))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
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

    /** 연 배당률(분수) 기준 월 배당액(원, 정수). */
    private BigDecimal monthlyByRate(BigDecimal amount, BigDecimal annualRate) {
        return nz(amount).multiply(annualRate)
                .divide(MONTHS_PER_YEAR, 0, RoundingMode.HALF_UP);
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
