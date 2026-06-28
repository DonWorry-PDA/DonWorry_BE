package com.sol.user.pension.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class PensionResourceResponse {

    private BigDecimal totalMonthlyPension;
    private BigDecimal totalMonthlyPensionNet;
    private List<PensionItem> pensions;

    @Getter
    @Builder
    public static class PensionItem {
        private String type;
        private String label;
        private String institutionName;
        private Integer startAge;
        private BigDecimal currentBalance;
        private BigDecimal retirementAmount;
        private BigDecimal personalAmount;
        private BigDecimal expectedMonthlyGross;
        private BigDecimal expectedMonthlyNet;
        private BigDecimal effectiveTaxRate;
        private BigDecimal taxBenefitLimit;
        private boolean estimated;
        private Integer payoutMonths;
        private Integer yearsEnrolled;
    }
}
