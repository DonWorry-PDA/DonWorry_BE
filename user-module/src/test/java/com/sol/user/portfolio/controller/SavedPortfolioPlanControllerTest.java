package com.sol.user.portfolio.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sol.common.exception.GlobalExceptionHandler;
import com.sol.user.portfolio.dto.SavePlanRequest;
import com.sol.user.portfolio.dto.SavePlanResponse;
import com.sol.user.portfolio.dto.SavedPlanResponse;
import com.sol.user.portfolio.service.SavedPortfolioPlanService;
import com.sol.user.portfolio.type.PlanType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SavedPortfolioPlanControllerTest {

    @Mock SavedPortfolioPlanService service;
    @InjectMocks SavedPortfolioPlanController controller;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Test
    void POST_정상_요청_200_savedAt_반환() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 6, 30, 12, 0, 0);
        when(service.save(eq(1L), any(SavePlanRequest.class))).thenReturn(new SavePlanResponse(now));

        mockMvc.perform(post("/api/user/portfolio/saved-plan")
                        .requestAttr("userId", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest(PlanType.STABLE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.savedAt").exists());
    }

    @Test
    void POST_planType_없으면_400() throws Exception {
        mockMvc.perform(post("/api/user/portfolio/saved-plan")
                        .requestAttr("userId", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "monthlyIncome": 1680000,
                                  "holdings": [{"productId": 101, "ticker": "069500", "productName": "KODEX 200", "weight": 0.6, "targetAmount": 120000000}]
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void POST_holdings_비어있으면_400() throws Exception {
        mockMvc.perform(post("/api/user/portfolio/saved-plan")
                        .requestAttr("userId", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "planType": "STABLE",
                                  "monthlyIncome": 1680000,
                                  "holdings": []
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void GET_저장된_설계안_있으면_200_planType_반환() throws Exception {
        SavedPlanResponse response = new SavedPlanResponse(
                PlanType.STABLE,
                new BigDecimal("1680000"),
                new BigDecimal("59.00"), new BigDecimal("84.00"),
                new BigDecimal("900000"), BigDecimal.ZERO,
                new BigDecimal("200000000"),
                List.of(),
                LocalDateTime.of(2026, 6, 30, 12, 0, 0));
        when(service.getSaved(1L)).thenReturn(response);

        mockMvc.perform(get("/api/user/portfolio/saved-plan")
                        .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.planType").value("STABLE"))
                .andExpect(jsonPath("$.data.monthlyIncome").value(1680000));
    }

    @Test
    void GET_저장된_설계안_없으면_200_data_null() throws Exception {
        when(service.getSaved(1L)).thenReturn(null);

        mockMvc.perform(get("/api/user/portfolio/saved-plan")
                        .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void POST_holdings_productId_없으면_400() throws Exception {
        mockMvc.perform(post("/api/user/portfolio/saved-plan")
                        .requestAttr("userId", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "planType": "STABLE",
                                  "monthlyIncome": 1680000,
                                  "principalAmount": 200000000,
                                  "holdings": [{"ticker": "069500", "productName": "KODEX 200", "weight": 0.6, "targetAmount": 120000000}]
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void DELETE_저장된_설계안_삭제_200() throws Exception {
        mockMvc.perform(delete("/api/user/portfolio/saved-plan")
                        .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));

        verify(service).delete(1L);
    }

    private SavePlanRequest validRequest(PlanType planType) {
        return new SavePlanRequest(
                planType,
                new BigDecimal("1680000"),
                new BigDecimal("59.00"), new BigDecimal("84.00"),
                new BigDecimal("900000"), BigDecimal.ZERO,
                new BigDecimal("200000000"),
                List.of(new SavePlanRequest.HoldingItem(101L, "069500", "KODEX 200",
                        new BigDecimal("0.60"), new BigDecimal("120000000"))));
    }
}
