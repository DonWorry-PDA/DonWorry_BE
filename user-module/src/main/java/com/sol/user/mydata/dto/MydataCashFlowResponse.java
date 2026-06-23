package com.sol.user.mydata.dto;

import com.sol.user.cashflow.entity.CashFlowEvent;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MydataCashFlowResponse(
        Long eventId,
        LocalDate eventDate,
        String eventType,
        String title,
        BigDecimal amount,
        String flowType,
        String status,
        Boolean recurring,
        String source
) {
    public static MydataCashFlowResponse from(CashFlowEvent event) {
        return new MydataCashFlowResponse(
                event.getEventId(),
                event.getEventDate(),
                event.getEventType(),
                event.getTitle(),
                event.getAmount(),
                event.getFlowType(),
                event.getStatus(),
                event.getRecurring(),
                event.getSource()
        );
    }
}
