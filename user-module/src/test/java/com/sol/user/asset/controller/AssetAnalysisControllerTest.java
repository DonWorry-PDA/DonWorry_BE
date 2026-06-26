package com.sol.user.asset.controller;

import com.sol.common.exception.GlobalExceptionHandler;
import com.sol.user.asset.dto.AssetCompositionResponse;
import com.sol.user.asset.dto.AssetIncomeResponse;
import com.sol.user.asset.dto.AssetScheduleResponse;
import com.sol.user.asset.service.AssetCompositionService;
import com.sol.user.asset.service.AssetIncomeService;
import com.sol.user.asset.service.AssetScheduleService;
import com.sol.user.pension.dto.PensionResourceResponse;
import com.sol.user.pension.service.PensionResourceService;
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
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AssetAnalysisControllerTest {

    @Mock private AssetCompositionService assetCompositionService;
    @Mock private AssetIncomeService assetIncomeService;
    @Mock private AssetScheduleService assetScheduleService;
    @Mock private PensionResourceService pensionResourceService;

    @InjectMocks private AssetAnalysisController assetAnalysisController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(assetAnalysisController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("자산 구성 조회 → 200, 총자산·순자산 반환")
    void getComposition_returns200() throws Exception {
        AssetCompositionResponse mockResponse = AssetCompositionResponse.builder()
                .totalAsset(new BigDecimal("10000000"))
                .totalDebt(new BigDecimal("2000000"))
                .netWorth(new BigDecimal("8000000"))
                .allocation(List.of())
                .groups(List.of())
                .build();

        when(assetCompositionService.getComposition(1L)).thenReturn(mockResponse);

        mockMvc.perform(get("/api/user/asset/analysis/composition")
                        .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.totalAsset").value(10000000))
                .andExpect(jsonPath("$.data.netWorth").value(8000000));
    }

    @Test
    @DisplayName("월 수입 조회 → 200, totalMonthlyIncome 반환")
    void getIncome_returns200() throws Exception {
        AssetIncomeResponse mockResponse = AssetIncomeResponse.builder()
                .totalMonthlyIncome(new BigDecimal("500000"))
                .accessibleIncome(new BigDecimal("400000"))
                .lockedIncome(new BigDecimal("100000"))
                .totalUnrealizedGainLoss(new BigDecimal("50000"))
                .sources(List.of())
                .build();

        when(assetIncomeService.getIncome(1L)).thenReturn(mockResponse);

        mockMvc.perform(get("/api/user/asset/analysis/income")
                        .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.totalMonthlyIncome").value(500000))
                .andExpect(jsonPath("$.data.accessibleIncome").value(400000));
    }

    @Test
    @DisplayName("현금 일정 조회 (기본 3개월) → 200, events 반환")
    void getSchedule_defaultMonths_returns200() throws Exception {
        AssetScheduleResponse mockResponse = AssetScheduleResponse.builder()
                .events(List.of())
                .build();

        when(assetScheduleService.getSchedule(1L, 3)).thenReturn(mockResponse);

        mockMvc.perform(get("/api/user/asset/analysis/schedule")
                        .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.events").isEmpty());
    }

    @Test
    @DisplayName("현금 일정 조회 (months=6) → 200, events 반환")
    void getSchedule_customMonths_returns200() throws Exception {
        AssetScheduleResponse mockResponse = AssetScheduleResponse.builder()
                .events(List.of())
                .build();

        when(assetScheduleService.getSchedule(1L, 6)).thenReturn(mockResponse);

        mockMvc.perform(get("/api/user/asset/analysis/schedule")
                        .param("months", "6")
                        .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.events").isEmpty());
    }

    @Test
    @DisplayName("연금 재원 조회 → 200, totalMonthlyPension 반환")
    void getPension_returns200() throws Exception {
        PensionResourceResponse mockResponse = PensionResourceResponse.builder()
                .totalMonthlyPension(new BigDecimal("1200000"))
                .pensions(List.of())
                .build();

        when(pensionResourceService.getPension(1L)).thenReturn(mockResponse);

        mockMvc.perform(get("/api/user/asset/analysis/pension")
                        .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.totalMonthlyPension").value(1200000));
    }
}
