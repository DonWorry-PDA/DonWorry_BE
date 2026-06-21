package com.sol.user.portfolio.service;

import com.sol.user.portfolio.calculator.OperationGradeCalculator;
import com.sol.user.portfolio.dto.OperationGradeInput;
import com.sol.user.portfolio.dto.OperationGradeResponse;
import com.sol.user.portfolio.dto.OperationGradeResult;
import com.sol.user.portfolio.type.Gender;
import com.sol.user.portfolio.type.InvestmentPropensity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class OperationGradeService {

    private final OperationGradeCalculator calculator;

    public OperationGradeResponse calculate(Long userId) {
        OperationGradeInput input = createMockInput();
        OperationGradeResult result = calculator.calculate(input);
        return toResponse(result);
    }

    // TODO: 마이데이터 연동 후 실제 데이터로 교체
    private OperationGradeInput createMockInput() {
        return OperationGradeInput.builder()
                .age(65)
                .gender(Gender.MALE)
                .totalAsset(BigDecimal.valueOf(600_000_000))
                .pensionSaving(BigDecimal.valueOf(50_000_000))
                .targetMonthlyLivingCost(BigDecimal.valueOf(3_000_000))
                .essentialRatio(new BigDecimal("0.72"))
                .monthlyNationalPension(BigDecimal.valueOf(1_000_000))
                .availableFinancialAsset(BigDecimal.valueOf(500_000_000))
                .hasLossInsurance(true)
                .hasMajorIllnessInsurance(true)
                .monthlyLoanRepayment(BigDecimal.ZERO)
                .q1(2)
                .q2(1)
                .investmentPropensity(InvestmentPropensity.NEUTRAL)
                .build();
    }

    private OperationGradeResponse toResponse(OperationGradeResult result) {
        return OperationGradeResponse.builder()
                .remainingYears(result.getRemainingYears())
                .floorAsset(result.getFloorAsset())
                .surplus(result.getSurplus())
                .floorScore(result.getFloorScore())
                .bufferScore(result.getBufferScore())
                .medicalScore(result.getMedicalScore())
                .debtScore(result.getDebtScore())
                .capabilityScore(result.getCapabilityScore())
                .preferenceScore(result.getPreferenceScore())
                .operationScore(result.getOperationScore())
                .operationGrade(result.getOperationGrade())
                .finalGrade(result.getFinalGrade())
                .build();
    }
}
