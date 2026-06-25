package com.sol.user.trade.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record TransferRequest(
        @NotEmpty List<@NotNull @Valid TransferItem> transfers
) {
    public record TransferItem(
            @NotNull Long fromAccountId,
            @NotNull @DecimalMin(value = "1") @Digits(integer = 15, fraction = 0) BigDecimal amount
    ) {}
}
