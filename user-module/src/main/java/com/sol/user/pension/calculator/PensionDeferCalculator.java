package com.sol.user.pension.calculator;

import com.sol.user.pension.dto.PensionDeferComparisonRow;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
public class PensionDeferCalculator {

    public static final List<Integer> DEFER_RATE_OPTIONS = List.of(0, 50, 60, 70, 80, 90, 100);
    private static final BigDecimal ANNUAL_BONUS_RATE = new BigDecimal("0.072");

    public List<PensionDeferComparisonRow> calcAllRows(
            BigDecimal base, int deferYears,
            BigDecimal dividendIncome, BigDecimal targetLivingCost) {
        if (base == null || base.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("base must be positive, got: " + base);
        if (deferYears < 1)
            throw new IllegalArgumentException("deferYears must be >= 1, got: " + deferYears);
        return DEFER_RATE_OPTIONS.stream()
            .map(rate -> calcRow(base, rate, deferYears, dividendIncome, targetLivingCost))
            .toList();
    }

    public PensionDeferComparisonRow calcRow(
            BigDecimal base, int deferRate, int deferYears,
            BigDecimal dividendIncome, BigDecimal targetLivingCost) {
        if (base == null || base.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("base must be positive, got: " + base);
        if (deferYears < 1)
            throw new IllegalArgumentException("deferYears must be >= 1, got: " + deferYears);
        long during = calcDuringDeferMonthly(base, deferRate);
        long after = calcAfterDeferMonthly(base, deferRate, deferYears);
        Long breakEven = deferRate == 0 ? null
            : calcBreakEvenMonths(base, deferRate, deferYears, after);
        int rateDuring = calcCoverageRate(BigDecimal.valueOf(during), dividendIncome, targetLivingCost);
        int rateAfter = calcCoverageRate(BigDecimal.valueOf(after), dividendIncome, targetLivingCost);
        return PensionDeferComparisonRow.builder()
            .deferRate(deferRate)
            .duringDeferMonthly(during)
            .afterDeferMonthly(after)
            .monthlyIncrease(after - base.setScale(0, RoundingMode.DOWN).longValue())
            .breakEvenMonths(breakEven)
            .coverageRateDuring(rateDuring)
            .coverageRateAfter(rateAfter)
            .stabilityDuring(toStabilityLabel(rateDuring))
            .stabilityAfter(toStabilityLabel(rateAfter))
            .build();
    }

    public String generateInsight(int deferRate, List<PensionDeferComparisonRow> rows) {
        PensionDeferComparisonRow selected = rows.stream()
            .filter(r -> r.deferRate() == deferRate)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No row found for deferRate: " + deferRate));

        String primary;
        if (deferRate == 0) {
            primary = "현재 국민연금을 즉시 수령합니다.";
        } else if (deferRate == 100) {
            primary = "향후 월 연금은 가장 많이 증가하지만 연기 기간 동안 생활비 공백이 발생합니다.";
        } else if (selected.coverageRateDuring() >= 70) {
            primary = "현재 생활비를 일부 확보하면서 향후 월 연금을 늘릴 수 있습니다.";
        } else {
            primary = "연기 기간 동안 생활비 공백이 발생할 수 있습니다.";
        }

        int bestRate = rows.stream()
            .filter(r -> r.coverageRateDuring() >= 70)
            .mapToInt(PensionDeferComparisonRow::deferRate)
            .max()
            .orElse(-1);
        String recommendation = bestRate >= 0
            ? " 현재 생활 안정도 기준으로는 " + bestRate + "% 연기안이 가장 적합해 보여요."
            : " 즉시 수령을 유지하는 것이 안전해 보여요.";
        return primary + recommendation;
    }

    // package-private for testing
    long calcDuringDeferMonthly(BigDecimal base, int deferRate) {
        return base.multiply(BigDecimal.valueOf(100 - deferRate))
            .divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN)
            .longValue();
    }

    // package-private for testing
    long calcAfterDeferMonthly(BigDecimal base, int deferRate, int deferYears) {
        BigDecimal immediate = base.multiply(BigDecimal.valueOf(100 - deferRate))
            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal deferred = base.multiply(BigDecimal.valueOf(deferRate))
            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal bonusFactor = BigDecimal.ONE.add(ANNUAL_BONUS_RATE.multiply(BigDecimal.valueOf(deferYears)));
        return immediate.add(deferred.multiply(bonusFactor))
            .setScale(0, RoundingMode.DOWN)
            .longValue();
    }

    // package-private for testing
    long calcBreakEvenMonths(BigDecimal base, int deferRate, int deferYears, long afterDeferMonthly) {
        BigDecimal lostTotal = base.multiply(BigDecimal.valueOf(deferRate))
            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(12L * deferYears));
        long monthlyGain = afterDeferMonthly - base.setScale(0, RoundingMode.DOWN).longValue();
        if (monthlyGain <= 0) {
            throw new IllegalArgumentException("monthlyGain must be positive: deferRate=" + deferRate + ", deferYears=" + deferYears);
        }
        return lostTotal.divide(BigDecimal.valueOf(monthlyGain), 0, RoundingMode.CEILING)
            .longValue();
    }

    // package-private for testing
    int calcCoverageRate(BigDecimal monthlyPension, BigDecimal dividendIncome, BigDecimal targetLivingCost) {
        if (targetLivingCost == null || targetLivingCost.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("targetLivingCost must be positive, got: " + targetLivingCost);
        }
        BigDecimal dividend = dividendIncome != null ? dividendIncome : BigDecimal.ZERO;
        return monthlyPension.add(dividend)
            .multiply(BigDecimal.valueOf(100))
            .divide(targetLivingCost, 0, RoundingMode.DOWN)
            .intValue();
    }

    // package-private for testing
    String toStabilityLabel(int coverageRate) {
        return coverageRate >= 70 ? "안정" : "주의";
    }
}
