package com.sol.user.asset.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

/**
 * 자산관리 메인 허브 집계 응답.
 * changeAmount / changeDirection 은 월간 자산 스냅샷(#6)이 도입되기 전까지 null / "FLAT" 로 내려간다.
 */
@Builder
public record AssetHubResponse(
        BigDecimal totalAsset,
        BigDecimal changeAmount,
        String changeDirection,          // UP | DOWN | FLAT
        List<AssetAllocationItem> allocation,
        BigDecimal monthlyIncome,
        BigDecimal monthlyExpense,
        List<EtfHoldingItem> etfHoldings,
        BigDecimal etfSnapshotAmount,
        AssetHubMenus menus
) {
    public record EtfHoldingItem(String ticker, BigDecimal quantity) {}
}
