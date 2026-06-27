package com.sol.user.report.dto;

import java.math.BigDecimal;
import java.util.List;

public record MonthlyReportResponse(
        String month,
        List<String> summary,
        AssetChangeSection assetChange,
        IncomeSection income,
        SpendingSection spending,
        NextMonthPreviewSection nextMonthPreview
) {

    public record AssetChangeSection(
            BigDecimal changeAmount,   // null = 이전 달 스냅샷 없음
            BigDecimal previousTotal,  // null = 이전 달 스냅샷 없음
            BigDecimal currentTotal
    ) {}

    public record IncomeSection(
            BigDecimal pensionAmount,
            BigDecimal dividendAmount,
            BigDecimal dividendChangeRate, // null = 이전 달 데이터 없음, 단위 %
            BigDecimal interestAmount
    ) {}

    public record SpendingSection(
            BigDecimal expenseAmount,
            BigDecimal incomeAmount,
            String judgment,     // "적정" / "주의" / "과다"
            int spendingRatio    // expense / income × 100. income=0이면 expense>0 → 999, 둘 다 0 → 0
    ) {}

    public record NextMonthPreviewSection(
            BigDecimal incomingTotal,
            BigDecimal pensionAmount,
            BigDecimal dividendAmount,
            BigDecimal outgoingTotal,
            boolean balanceSufficient
    ) {}
}
