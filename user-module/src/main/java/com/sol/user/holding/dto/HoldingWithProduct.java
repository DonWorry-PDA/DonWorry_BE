package com.sol.user.holding.dto;

import java.math.BigDecimal;

public interface HoldingWithProduct {
    Long getHoldingId();

    Long getProductId();

    BigDecimal getEvaluationAmount();
}
