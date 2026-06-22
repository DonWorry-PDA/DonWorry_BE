package com.sol.user.asset.dto;

import com.sol.user.asset.type.MockType;
import com.sol.user.stability.dto.LifeStabilityResponse;

import java.time.LocalDateTime;

public record MockAssetResponse(
        MockType mockType,
        LocalDateTime generatedAt,
        AssetSummaryResponse assetSummary,
        LifeStabilityResponse lifeStability,
        MockGeneratedCounts generatedCounts
) {
}
