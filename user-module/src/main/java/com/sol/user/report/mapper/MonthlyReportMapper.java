package com.sol.user.report.mapper;

import com.sol.user.report.dto.MonthlyReportResponse;
import com.sol.user.report.dto.MonthlyReportResponse.AssetChangeSection;
import com.sol.user.report.dto.MonthlyReportResponse.IncomeSection;
import com.sol.user.report.dto.MonthlyReportResponse.NextMonthPreviewSection;
import com.sol.user.report.dto.MonthlyReportResponse.SpendingSection;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

@Component
public class MonthlyReportMapper {

    public MonthlyReportResponse toResponse(
            YearMonth ym,
            List<String> summary,
            AssetChangeSection assetChange,
            IncomeSection income,
            SpendingSection spending,
            NextMonthPreviewSection nextMonthPreview) {
        return new MonthlyReportResponse(ym.toString(), summary, assetChange, income, spending, nextMonthPreview);
    }

    public AssetChangeSection toAssetChange(
            BigDecimal changeAmount, BigDecimal previousTotal, BigDecimal currentTotal) {
        return new AssetChangeSection(changeAmount, previousTotal, currentTotal);
    }

    public IncomeSection toIncome(
            BigDecimal receivedPension, BigDecimal dividendAmount,
            BigDecimal dividendChangeRate, BigDecimal interestAmount) {
        return new IncomeSection(receivedPension, dividendAmount, dividendChangeRate, interestAmount);
    }

    public SpendingSection toSpending(
            BigDecimal expenseAmount, BigDecimal incomeAmount, String judgment, int spendingRatio) {
        return new SpendingSection(expenseAmount, incomeAmount, judgment, spendingRatio);
    }

    public NextMonthPreviewSection toNextMonthPreview(
            BigDecimal incomingTotal, BigDecimal nextPension,
            BigDecimal nextDividend, BigDecimal outgoingTotal, boolean balanceSufficient) {
        return new NextMonthPreviewSection(incomingTotal, nextPension, nextDividend, outgoingTotal, balanceSufficient);
    }
}
