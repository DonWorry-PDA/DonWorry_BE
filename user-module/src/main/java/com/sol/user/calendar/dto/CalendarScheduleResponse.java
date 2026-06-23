package com.sol.user.calendar.dto;

import com.sol.user.calendar.type.CalendarEventCategory;

import java.math.BigDecimal;

public record CalendarScheduleResponse(
        String id,
        CalendarEventCategory category,
        String title,
        BigDecimal amountKrw,
        boolean estimated
) {
}
