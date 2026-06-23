package com.sol.user.holding.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface HoldingDividendCalendarProjection {

    Long getProductId();

    String getProductName();

    BigDecimal getQuantity();

    BigDecimal getAmountPerUnit();

    LocalDate getLatestPaymentDate();

    Integer getDistributionIntervalMonths();
}
