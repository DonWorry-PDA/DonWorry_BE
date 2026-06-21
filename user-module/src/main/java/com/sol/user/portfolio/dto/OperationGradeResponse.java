package com.sol.user.portfolio.dto;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record OperationGradeResponse(
        // STEP1
        int remainingYears,
        BigDecimal floorAsset,
        BigDecimal surplus,

        // STEP2
        BigDecimal floorScore,
        BigDecimal bufferScore,
        BigDecimal medicalScore,
        BigDecimal debtScore,
        BigDecimal capabilityScore,

        // STEP3
        BigDecimal preferenceScore,

        // STEP4
        BigDecimal operationScore,
        int operationGrade,
        int finalGrade
) {
}
