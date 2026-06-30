package com.sol.user.calendar.mapper;

import com.sol.user.calendar.type.CalendarEventCategory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Locale;

@Component
public class CalendarEventMapper {

    public CalendarEventCategory toCategory(String eventType) {
        if (eventType == null) {
            return CalendarEventCategory.ETC;
        }

        return switch (eventType.toUpperCase(Locale.ROOT)) {
            case "DIVIDEND" -> CalendarEventCategory.DIVIDEND;
            case "PENSION" -> CalendarEventCategory.PENSION;
            case "INTEREST" -> CalendarEventCategory.INTEREST;
            case "PAYMENT", "TRANSFER", "CONTRIBUTION",
                    "MAINTENANCE", "INSURANCE", "LOAN",
                    "UTILITY", "PHONE" -> CalendarEventCategory.PAYMENT;
            case "CARD", "TRANSPORT", "MEDICAL" -> CalendarEventCategory.TRANSACTION;
            case "STOCK_BUY", "STOCK_SELL" -> CalendarEventCategory.INVESTMENT;
            case "MATURITY" -> CalendarEventCategory.MATURITY;
            default -> CalendarEventCategory.ETC;
        };
    }

    public BigDecimal toSignedAmount(BigDecimal amount, String flowType) {
        if (amount == null) {
            return null;
        }
        if (flowType == null) {
            return amount;
        }

        return switch (flowType.toUpperCase(Locale.ROOT)) {
            case "INCOME" -> amount.abs();
            case "EXPENSE" -> amount.abs().negate();
            default -> amount;
        };
    }
}
