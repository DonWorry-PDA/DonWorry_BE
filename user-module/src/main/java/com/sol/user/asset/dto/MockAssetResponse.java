package com.sol.user.asset.dto;

import com.sol.user.asset.type.MockType;

import java.time.LocalDateTime;

public record MockAssetResponse(
        MockType mockType,
        LocalDateTime generatedAt,
        AssetSummaryResponse assetSummary,
        MockGeneratedCounts generatedCounts
) {
}
