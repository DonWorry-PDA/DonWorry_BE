package com.sol.product.product.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class EtfDetailDto {

    private String tickerCode;
    private String assetManager;
    private String brandName;
    private Integer riskGrade;
    private BigDecimal annualDividendRate;
    private BigDecimal latestDividendRate;
    private String distributionCycle;
    private Integer distributionIntervalMonths;
    private BigDecimal high52w;
    private BigDecimal low52w;
    private String retirementPensionLimit;
    private Boolean personalPensionAvailable;

    // daily_price 최신 레코드
    private BigDecimal closingPrice;
    private BigDecimal priceChange;
    private BigDecimal changeRate;
    private BigDecimal nav;
    private BigDecimal netAssetTotal;

    private String prospectusUrl;
    private String simplifiedUrl;
    private String fundRulesUrl;
}
