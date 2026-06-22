package com.sol.user.stability.dto;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record LifeStabilityCalculationInput(
        BigDecimal targetMonthlyLivingExpense,
        BigDecimal monthlyIncome,
        BigDecimal monthlyFixedExpense,
        BigDecimal monthlyEssentialExpense,
        BigDecimal monthlyLoanRepayment,
        BigDecimal monthlyFinancialIncome,
        BigDecimal monthlyRiskAssetWithdrawal,
        BigDecimal liquidAsset,
        BigDecimal medicalPreparedAsset,
        BigDecimal expectedAnnualMedicalExpense
) {
}
