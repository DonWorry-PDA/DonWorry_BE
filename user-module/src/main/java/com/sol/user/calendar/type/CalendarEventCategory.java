package com.sol.user.calendar.type;

import com.fasterxml.jackson.annotation.JsonValue;

public enum CalendarEventCategory {
    DIVIDEND("dividend", "배당"),
    PENSION("pension", "연금"),
    PAYMENT("payment", "납입"),
    TRANSACTION("transaction", "소비"),
    INVESTMENT("investment", "투자"),
    MATURITY("maturity", "만기"),
    INTEREST("interest", "예금이자"),
    ETC("etc", "기타");

    private final String value;
    private final String shortLabel;

    CalendarEventCategory(String value, String shortLabel) {
        this.value = value;
        this.shortLabel = shortLabel;
    }

    @JsonValue
    public String value() {
        return value;
    }

    public String shortLabel() {
        return shortLabel;
    }
}
