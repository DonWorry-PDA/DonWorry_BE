package com.sol.user.pension.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class PensionResourceResponse {

    private BigDecimal totalMonthlyPension;
    private List<PensionItem> pensions;

    @Getter
    @Builder
    public static class PensionItem {
        private String type;
        private String label;
        private String institutionName;
        private Integer startAge;
        private BigDecimal currentBalance;
        private BigDecimal expectedMonthly;
        private BigDecimal taxBenefitLimit;
        private boolean estimated;
        /** estimated=true 항목에만 설정. 수령 개시 나이부터 기대수명까지 개월 수 */
        private Integer payoutMonths;
    }
}
