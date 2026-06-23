package com.sol.user.portfolio.dto;

import com.sol.user.portfolio.type.InvestmentPropensity;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record OperationGradeInput(
        // STEP1 — 바닥자산·여유분
        int age,
        BigDecimal totalAsset,
        BigDecimal pensionSaving,
        BigDecimal targetMonthlyLivingCost,
        BigDecimal essentialRatio,             // 기본 0.72, 범위 0.60~0.85
        BigDecimal monthlyNationalPension,

        // STEP2 — 능력 점수
        BigDecimal availableFinancialAsset,    // 가용금융자산 (부동산 제외)
        boolean hasLossInsurance,              // 실손보험 보유
        boolean hasMajorIllnessInsurance,      // 장기요양·중대질병 보험 보유
        BigDecimal monthlyLoanRepayment,

        // STEP3 — 선호 점수
        int q1,   // 손실 감내 0~3
        int q2,   // 현금흐름 vs 성장 0~2

        // STEP4 — 성향 cap
        InvestmentPropensity investmentPropensity
) {
}
