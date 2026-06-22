package com.sol.user.holding.dto;

import java.math.BigDecimal;

public interface HoldingWithProduct {
    Long getHoldingId();

    String getProductName();

    String getProductType();

    BigDecimal getEvaluationAmount();
}
