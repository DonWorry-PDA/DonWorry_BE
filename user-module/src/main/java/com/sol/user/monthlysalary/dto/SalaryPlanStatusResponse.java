package com.sol.user.monthlysalary.dto;

import com.sol.user.monthlysalary.type.GuidanceAction;
import com.sol.user.monthlysalary.type.ReentryEmphasis;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 현재 운용 현황 — 기이용자(plan 보유)에게만 본문이 채워진다.
 * plan이 없으면 {@code hasPlan=false}만 내려가고 나머지는 null → FE가 최초 진입(자산 선택)으로 라우팅.
 * 재진입(plan 보유)일 때는 {@code reentryGuidance}로 다음 행동(생활비 상향/재설문)을 안내한다.
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
        List<HoldingStatus> holdings,
        ReentryGuidance reentryGuidance
) {

    /** ACTIVE plan 없음 — 최초 진입 분기. reentryGuidance는 null. */
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

    /** 재진입 안내 — 항상 두 선택지를 제시하고, emphasis로 강조 대상만 바꾼다. */
    public record ReentryGuidance(
            ReentryEmphasis emphasis,
            List<GuidanceOption> options
    ) {
    }

    /** 선택지 한 줄 — action enum이 가진 라우트/라벨을 펼쳐 FE 계약으로 노출. */
    public record GuidanceOption(
            GuidanceAction action,
            String route,
            String label
    ) {
        public static GuidanceOption of(GuidanceAction action) {
            return new GuidanceOption(action, action.getRoute(), action.getLabel());
        }
    }
}
