package com.sol.user.mydata.dto;

import com.sol.user.pension.entity.Pension;

import java.math.BigDecimal;

public record MydataPensionResponse(
        Long pensionId,
        String pensionType,
        BigDecimal expectedMonthlyAmount,
        Boolean variable,
        Integer startAge
) {
    public static MydataPensionResponse from(Pension pension) {
        return new MydataPensionResponse(
                pension.getPensionId(),
                pension.getPensionType(),
                pension.getExpectedMonthlyAmount(),
                pension.getVariable(),
                pension.getStartAge()
        );
    }
}
