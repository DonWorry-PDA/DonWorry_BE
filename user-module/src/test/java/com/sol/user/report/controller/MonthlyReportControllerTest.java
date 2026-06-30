package com.sol.user.report.controller;

import com.sol.common.exception.GlobalExceptionHandler;
import com.sol.user.report.dto.MonthlyReportResponse;
import com.sol.user.report.dto.MonthlyReportResponse.AssetChangeSection;
import com.sol.user.report.dto.MonthlyReportResponse.IncomeSection;
import com.sol.user.report.dto.MonthlyReportResponse.NextMonthPreviewSection;
import com.sol.user.report.dto.MonthlyReportResponse.SpendingSection;
import com.sol.user.report.service.MonthlyReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MonthlyReportControllerTest {

    @Mock private MonthlyReportService monthlyReportService;
    @InjectMocks private MonthlyReportController controller;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("month 파라미터 지정 → 200, 전체 섹션 반환")
    void getMonthlyReport_withMonth_returns200() throws Exception {
        when(monthlyReportService.getMonthlyReport(eq(1L), eq(YearMonth.of(2026, 6))))
                .thenReturn(sampleResponse("2026-06"));

        mockMvc.perform(get("/api/user/report/monthly")
                        .requestAttr("userId", 1L)
                        .param("month", "2026-06"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.month").value("2026-06"))
                .andExpect(jsonPath("$.data.summary", hasSize(3)))
                .andExpect(jsonPath("$.data.assetChange.currentTotal").value(250_000_000))
                .andExpect(jsonPath("$.data.income.pensionAmount").value(1_200_000))
                .andExpect(jsonPath("$.data.income.dividendAmount").value(100_000))
                .andExpect(jsonPath("$.data.income.dividendChangeRate").value(12.4))
                .andExpect(jsonPath("$.data.spending.judgment").value("적정"))
                .andExpect(jsonPath("$.data.nextMonthPreview.incomingTotal").value(1_332_450))
                .andExpect(jsonPath("$.data.nextMonthPreview.interestAmount").value(32_450))
                .andExpect(jsonPath("$.data.nextMonthPreview.balanceSufficient").value(true));
    }

    @Test
    @DisplayName("month 파라미터 생략 → 현재 달로 조회, 200 반환")
    void getMonthlyReport_noMonth_usesCurrentMonth() throws Exception {
        when(monthlyReportService.getMonthlyReport(eq(1L), any(YearMonth.class)))
                .thenReturn(sampleResponse(YearMonth.now().toString()));

        mockMvc.perform(get("/api/user/report/monthly")
                        .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("잘못된 month 형식 → 400 INVALID_INPUT")
    void getMonthlyReport_invalidMonth_returns400() throws Exception {
        mockMvc.perform(get("/api/user/report/monthly")
                        .requestAttr("userId", 1L)
                        .param("month", "2026/06"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));
    }

    @Test
    @DisplayName("이전 달 스냅샷 없으면 changeAmount null 반환")
    void getMonthlyReport_noSnapshot_changeAmountNull() throws Exception {
        MonthlyReportResponse noSnapshot = new MonthlyReportResponse(
                "2026-06",
                List.of("line1", "line2", "line3"),
                new AssetChangeSection(null, null, BigDecimal.valueOf(250_000_000)),
                new IncomeSection(BigDecimal.ZERO, BigDecimal.ZERO, null, BigDecimal.ZERO),
                new SpendingSection(BigDecimal.ZERO, BigDecimal.ZERO, "적정", 0),
                new NextMonthPreviewSection(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, true)
        );
        when(monthlyReportService.getMonthlyReport(eq(1L), eq(YearMonth.of(2026, 6))))
                .thenReturn(noSnapshot);

        mockMvc.perform(get("/api/user/report/monthly")
                        .requestAttr("userId", 1L)
                        .param("month", "2026-06"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assetChange.changeAmount").value(nullValue()))
                .andExpect(jsonPath("$.data.assetChange.previousTotal").value(nullValue()))
                .andExpect(jsonPath("$.data.assetChange.currentTotal").value(250_000_000));
    }

    private MonthlyReportResponse sampleResponse(String month) {
        return new MonthlyReportResponse(
                month,
                List.of("배당금이 지난달보다 12% 늘었어요.", "다음 달도 잔액 부족 없이 지낼 수 있어요.", "소비는 목표 안에서 잘 관리되고 있어요."),
                new AssetChangeSection(BigDecimal.valueOf(1_200_000), BigDecimal.valueOf(248_800_000), BigDecimal.valueOf(250_000_000)),
                new IncomeSection(BigDecimal.valueOf(1_200_000), BigDecimal.valueOf(100_000), new BigDecimal("12.4"), BigDecimal.valueOf(32_450)),
                new SpendingSection(BigDecimal.valueOf(2_180_000), BigDecimal.valueOf(3_000_000), "적정", 73),
                new NextMonthPreviewSection(BigDecimal.valueOf(1_332_450), BigDecimal.valueOf(1_200_000), BigDecimal.valueOf(100_000), BigDecimal.valueOf(32_450), BigDecimal.valueOf(2_150_000), true)
        );
    }
}
