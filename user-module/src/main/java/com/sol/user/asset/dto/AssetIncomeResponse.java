package com.sol.user.asset.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class AssetIncomeResponse {

    private BigDecimal totalMonthlyIncome;
    private BigDecimal accessibleIncome;
    private BigDecimal lockedIncome;
    private BigDecimal totalUnrealizedGainLoss;
    private List<IncomeSource> sources;

    @Getter
    @Builder
    public static class IncomeSource {
        private String type;
        private String label;
        private BigDecimal amount;
        private boolean locked;
    }
}
