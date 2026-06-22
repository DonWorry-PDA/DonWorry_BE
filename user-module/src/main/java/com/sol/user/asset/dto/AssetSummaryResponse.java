package com.sol.user.asset.dto;

import java.math.BigDecimal;
import java.util.List;

public record AssetSummaryResponse(
        BigDecimal totalAsset,
        BigDecimal totalDebt,
        BigDecimal netAsset,
        List<AssetGroupSummary> assetGroups
) {
}
