package com.sol.product.product.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class DepositDetailDto {

    private BigDecimal interestRate;
    private Integer maturityMonths;
    private BigDecimal subscriptionLimit;
    private Boolean depositInsurance;
}
