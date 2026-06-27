package com.sol.user.asset.dto;

import java.time.LocalDateTime;

/**
 * 사용자용 마이데이터 연결/동기화 응답.
 * 사용자는 시나리오(mockType)를 알 필요가 없으므로 노출하지 않는다.
 */
public record MydataSyncResponse(
        int connectedInstitutions,
        LocalDateTime syncedAt,
        String message,
        AssetSummaryResponse assetSummary
) {
    public static MydataSyncResponse from(MockAssetResponse result, String message) {
        return new MydataSyncResponse(
                result.connectedInstitutionCount(),
                result.generatedAt(),
                message,
                result.assetSummary()
        );
    }
}
