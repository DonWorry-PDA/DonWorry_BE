package com.sol.user.calendar.controller;

import com.sol.user.calendar.dto.CalendarEventResponse;
import com.sol.user.calendar.dto.CalendarMonthResponse;
import com.sol.user.calendar.dto.CalendarScheduleResponse;
import com.sol.user.calendar.service.CalendarQueryService;
import com.sol.user.calendar.type.CalendarEventCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CalendarControllerTest {

    @Mock
    private CalendarQueryService calendarQueryService;

    @InjectMocks
    private CalendarController calendarController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(calendarController).build();
    }

    @Test
    void getMonthReturnsFrontendCalendarShape() throws Exception {
        CalendarMonthResponse response = new CalendarMonthResponse(
                Map.of("2026-06-15", List.of(
                        new CalendarEventResponse(
                                CalendarEventCategory.DIVIDEND,
                                "배당",
                                BigDecimal.valueOf(350_000),
                                true
                        )
                )),
                Map.of("2026-06-15", List.of(
                        new CalendarScheduleResponse(
                                "etf-dividend-1-2026-06-15",
                                CalendarEventCategory.DIVIDEND,
                                "SOL ETF 예상 분배금",
                                BigDecimal.valueOf(350_000),
                                true
                        )
                )),
                Map.of()
        );
        when(calendarQueryService.getMonth(1L, 2026, 6)).thenReturn(response);

        mockMvc.perform(get("/api/user/calendar")
                        .requestAttr("userId", 1L)
                        .param("year", "2026")
                        .param("month", "6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.events['2026-06-15'][0].category")
                        .value("dividend"))
                .andExpect(jsonPath("$.data.events['2026-06-15'][0].short")
                        .value("배당"))
                .andExpect(jsonPath("$.data.events['2026-06-15'][0].estimated")
                        .value(true))
                .andExpect(jsonPath("$.data.schedules['2026-06-15'][0].title")
                        .value("SOL ETF 예상 분배금"))
                .andExpect(jsonPath("$.data.transactions").isEmpty());

        verify(calendarQueryService).getMonth(1L, 2026, 6);
    }
}
