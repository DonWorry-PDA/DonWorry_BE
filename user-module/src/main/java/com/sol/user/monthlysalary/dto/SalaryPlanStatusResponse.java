package com.sol.user.monthlysalary.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 현재 운용 현황 — 기이용자(ACTIVE plan 보유)에게만 본문이 채워진다.
 * ACTIVE plan이 없으면 {@code hasPlan=false}만 내려가고 나머지는 null → FE가 최초 진입(자산 선택)으로 라우팅.
 */
@Builder
public record SalaryPlanStatusResponse(
        boolean hasPlan,
        String planType,
        String displayName,
        BigDecimal expectedMonthlySalary,
        BigDecimal targetMonthlyLivingCost,
        BigDecimal livingCostCoverageRate,
        BigDecimal totalTargetAmount,
        BigDecimal totalCurrentEval,
        BigDecimal totalAchievedRate,
        LocalDateTime createdAt,
        List<HoldingStatus> holdings
) {

    /** ACTIVE plan 없음 — 최초 진입 분기. */
    public static SalaryPlanStatusResponse empty() {
        return SalaryPlanStatusResponse.builder().hasPlan(false).build();
    }

    @Builder
    public record HoldingStatus(
            Long productId,
            String productName,
            String bucketRole,
            BigDecimal targetAmount,
            BigDecimal currentEval,
            BigDecimal achievedRate,
            BigDecimal remainingToBuy,
            BigDecimal productContribution
    ) {
    }
}
