package com.sol.user.portfolio.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class OperationGradeResult {

    // STEP1
    private int remainingYears;
    private BigDecimal floorAsset;
    private BigDecimal availableAsset;    // 총자산 - 연금저축
    private BigDecimal surplus;

    // STEP2
    private BigDecimal floorScore;
    private BigDecimal bufferScore;
    private BigDecimal medicalScore;
    private BigDecimal debtScore;
    private BigDecimal capabilityScore;

    // STEP3
    private BigDecimal preferenceScore;

    // STEP4
    private BigDecimal operationScore;
    private int operationGrade;
    private int finalGrade;
}
