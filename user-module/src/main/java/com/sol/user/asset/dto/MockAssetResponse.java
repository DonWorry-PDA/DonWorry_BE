package com.sol.user.asset.dto;

import com.sol.user.asset.type.MockType;

import java.time.LocalDateTime;

public record MockAssetResponse(
        MockType mockType,
        LocalDateTime generatedAt,
        AssetSummaryResponse assetSummary,
        MockGeneratedCounts generatedCounts,
        // 연결된 기관 수(기관명 distinct, 전 도메인). generatedCounts.connections는 생성된 행 수라 별도.
        int connectedInstitutionCount
) {
}
