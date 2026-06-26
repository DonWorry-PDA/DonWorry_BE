package com.sol.user.holding.dto;

import java.math.BigDecimal;

public interface EtfHolding {
    Long getHoldingId();

    Long getProductId();

    BigDecimal getQuantity();
}
