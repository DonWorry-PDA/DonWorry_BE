package com.sol.user.monthlysalary.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * 월급 만들기 확정 요청 — 매수 완료 후, 유저가 실제로 보고 사들인 목표를 그대로 스냅샷.
 * 돈은 이미 {@code /trade/buy}에서 움직였으므로(거기서 검증) BE는 화면용 기록만 저장한다(결정 2).
 */
public record SalaryPlanConfirmRequest(
        @NotNull String planType,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal targetMonthlyLivingCost,
        @NotNull BigDecimal expectedMonthlySalary,
        @NotEmpty @Size(max = 100) @Valid List<HoldingItem> holdings
) {

    public record HoldingItem(
            @NotNull Long productId,
            @NotNull String productName,
            @NotNull String bucketRole,
            @NotNull String accountType,
            BigDecimal weight,
            @NotNull @DecimalMin(value = "0", inclusive = false, message = "목표 배분액은 0보다 커야 합니다.")
            BigDecimal targetAmount,
            BigDecimal productContribution
    ) {
    }
}
