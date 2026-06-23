package com.sol.user.calendar.mapper;

import com.sol.user.calendar.type.CalendarEventCategory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CalendarEventMapperTest {

    private final CalendarEventMapper mapper = new CalendarEventMapper();

    @Test
    void mapsDomainTypesToFrontendCategories() {
        assertThat(mapper.toCategory("DIVIDEND")).isEqualTo(CalendarEventCategory.DIVIDEND);
        assertThat(mapper.toCategory("PENSION")).isEqualTo(CalendarEventCategory.PENSION);
        assertThat(mapper.toCategory("INTEREST")).isEqualTo(CalendarEventCategory.INTEREST);
        assertThat(mapper.toCategory("MAINTENANCE")).isEqualTo(CalendarEventCategory.PAYMENT);
        assertThat(mapper.toCategory("INSURANCE")).isEqualTo(CalendarEventCategory.PAYMENT);
        assertThat(mapper.toCategory("CARD")).isEqualTo(CalendarEventCategory.PAYMENT);
        assertThat(mapper.toCategory("LOAN")).isEqualTo(CalendarEventCategory.PAYMENT);
        assertThat(mapper.toCategory("MATURITY")).isEqualTo(CalendarEventCategory.MATURITY);
        assertThat(mapper.toCategory("UNKNOWN")).isEqualTo(CalendarEventCategory.ETC);
        assertThat(mapper.toCategory(null)).isEqualTo(CalendarEventCategory.ETC);
    }

    @Test
    void signsAmountsFromFlowType() {
        assertThat(mapper.toSignedAmount(BigDecimal.valueOf(100), "INCOME"))
                .isEqualByComparingTo("100");
        assertThat(mapper.toSignedAmount(BigDecimal.valueOf(100), "EXPENSE"))
                .isEqualByComparingTo("-100");
        assertThat(mapper.toSignedAmount(BigDecimal.valueOf(-100), "INCOME"))
                .isEqualByComparingTo("100");
    }
}
