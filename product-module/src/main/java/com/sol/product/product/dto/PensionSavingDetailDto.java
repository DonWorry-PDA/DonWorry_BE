package com.sol.product.product.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class PensionSavingDetailDto {

    private String pensionSavingType;
    private String detailType;
    private BigDecimal avgReturnRate;
    private BigDecimal returnRate1Year;
    private BigDecimal returnRate2Year;
    private BigDecimal returnRate3Year;
    private String subscriptionMethod;
    private String providerName;
}
