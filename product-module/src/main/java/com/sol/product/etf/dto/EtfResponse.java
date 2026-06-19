package com.sol.product.etf.dto;

import com.sol.product.dividend.entity.DividendHistory;
import com.sol.product.etf.entity.EtfDetail;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Getter
@Builder
public class EtfResponse {

    private Long productId;
    private String productName;
    private String currency;
    private String tickerCode;
    private String assetManager;
    private String brandName;
    private String retirementPensionLimit;
    private Boolean personalPensionAvailable;
    private BigDecimal latestDividendRate;
    private BigDecimal annualDividendRate;
    private BigDecimal high52w;
    private BigDecimal low52w;
    private String distributionCycle;
    private Integer distributionIntervalMonths;
    private BigDecimal latestDividendAmount;
    private LocalDate latestDividendDate;
    private BigDecimal monthlyEquivalentDividendAmount;

    public static EtfResponse from(EtfDetail etfDetail) {
        return from(etfDetail, null);
    }

    public static EtfResponse from(EtfDetail etfDetail, DividendHistory latestDividend) {
        return EtfResponse.builder()
                .productId(etfDetail.getProduct().getProductId())
                .productName(etfDetail.getProduct().getProductName())
                .currency(etfDetail.getProduct().getCurrency())
                .tickerCode(etfDetail.getTickerCode())
                .assetManager(etfDetail.getAssetManager())
                .brandName(etfDetail.getBrandName())
                .retirementPensionLimit(etfDetail.getRetirementPensionLimit())
                .personalPensionAvailable(etfDetail.getPersonalPensionAvailable())
                .latestDividendRate(etfDetail.getLatestDividendRate())
                .annualDividendRate(etfDetail.getAnnualDividendRate())
                .high52w(etfDetail.getHigh52w())
                .low52w(etfDetail.getLow52w())
                .distributionCycle(etfDetail.getDistributionCycle())
                .distributionIntervalMonths(etfDetail.getDistributionIntervalMonths())
                .latestDividendAmount(latestDividend != null ? latestDividend.getAmountPerUnit() : null)
                .latestDividendDate(latestDividend != null ? latestDividend.getPaymentDate() : null)
                .monthlyEquivalentDividendAmount(calculateMonthlyEquivalentDividend(etfDetail, latestDividend))
                .build();
    }

    private static BigDecimal calculateMonthlyEquivalentDividend(
            EtfDetail etfDetail,
            DividendHistory latestDividend
    ) {
        if (latestDividend == null || latestDividend.getAmountPerUnit() == null
                || etfDetail.getDistributionIntervalMonths() == null
                || etfDetail.getDistributionIntervalMonths() <= 0) {
            return null;
        }

        return latestDividend.getAmountPerUnit()
                .divide(BigDecimal.valueOf(etfDetail.getDistributionIntervalMonths()), 2, RoundingMode.HALF_UP);
    }
}
