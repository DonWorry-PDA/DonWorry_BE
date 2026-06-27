package com.sol.user.terms.controller;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.common.exception.GlobalExceptionHandler;
import com.sol.user.terms.dto.TermsConsentResponse;
import com.sol.user.terms.dto.TermsStatusResponse;
import com.sol.user.terms.service.TermsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TermsControllerTest {

    @Mock
    private TermsService termsService;

    @InjectMocks
    private TermsController termsController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(termsController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ─── GET /api/user/terms/optional ──────────────────────────────────────

    @Test
    void returnsOptionalTermsStatus() throws Exception {
        TermsStatusResponse response = new TermsStatusResponse(
                new TermsStatusResponse.TermConsent(false, null),
                new TermsStatusResponse.TermConsent(true, LocalDate.of(2026, 6, 12))
        );
        when(termsService.getOptionalTerms(1L)).thenReturn(response);

        mockMvc.perform(get("/api/user/terms/optional")
                        .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.thirdParty.agreed").value(false))
                .andExpect(jsonPath("$.data.thirdParty.agreedAt").isEmpty())
                .andExpect(jsonPath("$.data.marketing.agreed").value(true))
                .andExpect(jsonPath("$.data.marketing.agreedAt").value("2026.06.12"));
    }

    // ─── PATCH /api/user/terms/{termId}/consent ─────────────────────────────

    @Test
    void updatesConsentAndReturnsResult() throws Exception {
        TermsConsentResponse response = new TermsConsentResponse("marketing", true, LocalDate.of(2026, 6, 27));
        when(termsService.updateConsent(eq(1L), eq("marketing"), any())).thenReturn(response);

        mockMvc.perform(patch("/api/user/terms/marketing/consent")
                        .requestAttr("userId", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agreed\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.termId").value("marketing"))
                .andExpect(jsonPath("$.data.agreed").value(true))
                .andExpect(jsonPath("$.data.agreedAt").value("2026.06.27"));
    }

    @Test
    void returns400ForInvalidTermId() throws Exception {
        when(termsService.updateConsent(eq(1L), eq("invalid"), any()))
                .thenThrow(new BaseException(ErrorCode.INVALID_TERM_ID));

        mockMvc.perform(patch("/api/user/terms/invalid/consent")
                        .requestAttr("userId", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agreed\": true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("수정할 수 없는 항목입니다."));
    }

    @Test
    void returns400WhenDisagreingAndAgreedAtIsNull() throws Exception {
        TermsConsentResponse response = new TermsConsentResponse("thirdParty", false, null);
        when(termsService.updateConsent(eq(1L), eq("thirdParty"), any())).thenReturn(response);

        mockMvc.perform(patch("/api/user/terms/thirdParty/consent")
                        .requestAttr("userId", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agreed\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.agreed").value(false))
                .andExpect(jsonPath("$.data.agreedAt").isEmpty());
    }
}
