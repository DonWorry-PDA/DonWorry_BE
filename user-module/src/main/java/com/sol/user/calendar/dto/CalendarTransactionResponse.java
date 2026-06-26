package com.sol.user.calendar.dto;

import java.math.BigDecimal;

public record CalendarTransactionResponse(
        String id,
        String date,
        String category,
        String title,
        BigDecimal amountKrw
) {
}
