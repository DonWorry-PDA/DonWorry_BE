package com.sol.user.pension.controller;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.common.exception.GlobalExceptionHandler;
import com.sol.user.pension.dto.PensionDeferDetail;
import com.sol.user.pension.dto.PensionDeferResponse;
import com.sol.user.pension.service.PensionDeferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class PensionDeferControllerTest {

    @Mock private PensionDeferService pensionDeferService;
    @InjectMocks private PensionDeferController pensionDeferController;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(pensionDeferController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    @DisplayName("정상 요청 → 200, code=SUCCESS, selected.deferRate=70")
    void compare_valid_returns200() throws Exception {
        PensionDeferResponse mockResponse = PensionDeferResponse.builder()
            .selected(PensionDeferDetail.builder()
                .deferRate(70).deferYears(5).basePensionMonthly(1_200_000)
                .duringDeferMonthly(360_000).afterDeferMonthly(1_502_400)
                .monthlyIncrease(302_400).breakEvenMonths(167L)
                .coverageRateBefore(59).coverageRateAfter(72)
                .insight("테스트 인사이트").build())
            .comparisonTable(List.of())
            .build();

        when(pensionDeferService.compare(1L, 70, 5)).thenReturn(mockResponse);

        mockMvc.perform(get("/api/user/asset/pension-defer")
                .requestAttr("userId", 1L)
                .param("deferRate", "70")
                .param("deferYears", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.selected.deferRate").value(70))
            .andExpect(jsonPath("$.data.selected.breakEvenMonths").value(167));
    }

    @Test
    @DisplayName("허용되지 않는 deferRate(30) → 컨트롤러 INVALID_INPUT → 400")
    void compare_invalidDeferRate_returns400() throws Exception {
        mockMvc.perform(get("/api/user/asset/pension-defer")
                .requestAttr("userId", 1L)
                .param("deferRate", "30")
                .param("deferYears", "5"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMON_001"));
    }

    @Test
    @DisplayName("국민연금 미등록 → PENSION_NOT_FOUND → 404")
    void compare_noPension_returns404() throws Exception {
        when(pensionDeferService.compare(1L, 70, 5))
            .thenThrow(new BaseException(ErrorCode.PENSION_NOT_FOUND));

        mockMvc.perform(get("/api/user/asset/pension-defer")
                .requestAttr("userId", 1L)
                .param("deferRate", "70")
                .param("deferYears", "5"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("PENSION_001"));
    }

    @Test
    @DisplayName("목표 생활비 미설정 → USER_GOAL_NOT_FOUND → 404")
    void compare_noUserGoal_returns404() throws Exception {
        when(pensionDeferService.compare(1L, 70, 5))
            .thenThrow(new BaseException(ErrorCode.USER_GOAL_NOT_FOUND));

        mockMvc.perform(get("/api/user/asset/pension-defer")
                .requestAttr("userId", 1L)
                .param("deferRate", "70")
                .param("deferYears", "5"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("USER_GOAL_001"));
    }
}
