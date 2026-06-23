package com.sol.user.calendar.dto;

import java.util.List;
import java.util.Map;

public record CalendarMonthResponse(
        Map<String, List<CalendarEventResponse>> events,
        Map<String, List<CalendarScheduleResponse>> schedules,
        Map<String, List<CalendarTransactionResponse>> transactions
) {
}
