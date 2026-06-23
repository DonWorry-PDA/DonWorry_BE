package com.sol.user.calendar.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sol.user.calendar.type.CalendarEventCategory;

import java.math.BigDecimal;

public record CalendarEventResponse(
        CalendarEventCategory category,
        @JsonProperty("short") String shortLabel,
        BigDecimal amountKrw,
        boolean estimated
) {
}
