package com.sol.user.holding.dto;

import java.math.BigDecimal;

public interface HoldingWithProduct {
    Long getHoldingId();
    Long getAccountId();   // NEW
    Long getProductId();
    BigDecimal getEvaluationAmount();
    BigDecimal getQuantity();
    String getAccountType();
}
