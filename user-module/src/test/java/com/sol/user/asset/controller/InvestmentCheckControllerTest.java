package com.sol.user.asset.controller;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.common.exception.GlobalExceptionHandler;
import com.sol.user.asset.dto.InvestmentCheckResponse;
import com.sol.user.asset.dto.InvestmentCheckResponse.GrowthAsset;
import com.sol.user.asset.dto.InvestmentCheckResponse.RoleContribution;
import com.sol.user.asset.service.InvestmentCheckService;
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

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InvestmentCheckControllerTest {

    @Mock private InvestmentCheckService investmentCheckService;
    @InjectMocks private InvestmentCheckController investmentCheckController;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(investmentCheckController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("정상 요청 → 200, 헤드라인 비율·역할·성장블록 반환")
    void check_valid_returns200() throws Exception {
        InvestmentCheckResponse mockResponse = InvestmentCheckResponse.builder()
                .cashflowAssetRatio(30)
                .totalAsset(BigDecimal.valueOf(100_000_000))
                .roles(List.of(
                        RoleContribution.builder().role("CASHFLOW").label("현금흐름")
                                .amount(BigDecimal.valueOf(30_000_000)).ratio(30)
                                .monthlyCashflow(BigDecimal.valueOf(87_500)).note("매달 배당·이자").build(),
                        RoleContribution.builder().role("GROWTH").label("성장")
                                .amount(BigDecimal.valueOf(70_000_000)).ratio(70)
                                .monthlyCashflow(BigDecimal.ZERO).note("성장").build()))
                .growthAsset(GrowthAsset.builder()
                        .amount(BigDecimal.valueOf(70_000_000))
                        .topStockName("삼성전자")
                        .concentrationRatio(57)
                        .concentrationLevel("보통")
                        .dividendNote("거의 없음")
                        .potentialMonthlyDividend(BigDecimal.valueOf(204_166))
                        .build())
                .build();

        when(investmentCheckService.check(1L)).thenReturn(mockResponse);

        mockMvc.perform(get("/api/user/asset/investment-check")
                        .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.cashflowAssetRatio").value(30))
                .andExpect(jsonPath("$.data.roles[0].role").value("CASHFLOW"))
                .andExpect(jsonPath("$.data.growthAsset.topStockName").value("삼성전자"))
                .andExpect(jsonPath("$.data.growthAsset.concentrationLevel").value("보통"));
    }

    @Test
    @DisplayName("자산 없는 사용자 → 200, 비율 0·성장블록 null")
    void check_noAsset_returns200() throws Exception {
        InvestmentCheckResponse empty = InvestmentCheckResponse.builder()
                .cashflowAssetRatio(0)
                .totalAsset(BigDecimal.ZERO)
                .roles(List.of())
                .growthAsset(null)
                .build();

        when(investmentCheckService.check(1L)).thenReturn(empty);

        mockMvc.perform(get("/api/user/asset/investment-check")
                        .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cashflowAssetRatio").value(0))
                .andExpect(jsonPath("$.data.roles").isEmpty())
                .andExpect(jsonPath("$.data.growthAsset").value(nullValue()));
    }

    @Test
    @DisplayName("상품 풀 미가용 → PRODUCT_POOL_UNAVAILABLE 전파")
    void check_productPoolUnavailable_propagates() throws Exception {
        when(investmentCheckService.check(1L))
                .thenThrow(new BaseException(ErrorCode.PRODUCT_POOL_UNAVAILABLE));

        mockMvc.perform(get("/api/user/asset/investment-check")
                        .requestAttr("userId", 1L))
                .andExpect(status().is5xxServerError());
    }
}
