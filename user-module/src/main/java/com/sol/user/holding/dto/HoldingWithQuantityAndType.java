package com.sol.user.holding.dto;

import java.math.BigDecimal;

public interface HoldingWithQuantityAndType {
    Long getProductId();
    BigDecimal getQuantity();
    String getAccountType();
}
