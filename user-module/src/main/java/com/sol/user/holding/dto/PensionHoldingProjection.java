package com.sol.user.holding.dto;

import java.math.BigDecimal;

public interface PensionHoldingProjection {
    Long getAccountId();
    BigDecimal getEvaluationAmount();
}
