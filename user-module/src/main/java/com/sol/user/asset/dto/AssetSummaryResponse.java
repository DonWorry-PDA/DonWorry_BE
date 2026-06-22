package com.sol.user.asset.dto;

import java.math.BigDecimal;
import java.util.List;

public record AssetSummaryResponse(
        BigDecimal totalAsset,
        BigDecimal totalDebt,
        BigDecimal netAsset,
        BigDecimal targetMonthlyLivingExpense,
        BigDecimal securedMonthlyCashflow,
        BigDecimal monthlyGap,
        BigDecimal cashflowCoverageRate,
        List<AssetGroupSummary> assetGroups
) {
}
