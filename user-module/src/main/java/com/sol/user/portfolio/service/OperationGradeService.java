package com.sol.user.portfolio.service;

import com.sol.user.portfolio.calculator.OperationGradeCalculator;
import com.sol.user.portfolio.dto.OperationGradeInput;
import com.sol.user.portfolio.dto.OperationGradeResponse;
import com.sol.user.portfolio.dto.OperationGradeResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OperationGradeService {

    private final OperationGradeCalculator calculator;
    private final OperationGradeInputAssembler inputAssembler;

    public OperationGradeResponse calculate(Long userId) {
        OperationGradeInput input = inputAssembler.assemble(userId);
        OperationGradeResult result = calculator.calculate(input);
        return toResponse(result);
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
