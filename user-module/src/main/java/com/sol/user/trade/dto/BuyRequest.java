package com.sol.user.trade.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record BuyRequest(
        @NotNull Long productId,
        @NotNull @DecimalMin(value = "0.0001", message = "매수 수량은 0보다 커야 합니다.") BigDecimal quantity
) {
}
