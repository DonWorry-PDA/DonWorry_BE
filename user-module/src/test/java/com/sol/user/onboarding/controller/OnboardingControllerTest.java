package com.sol.user.onboarding.controller;

import com.sol.user.onboarding.dto.OnboardingRequest;
import com.sol.user.onboarding.dto.OnboardingResponse;
import com.sol.user.onboarding.service.OnboardingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OnboardingControllerTest {

    @Mock
    private OnboardingService onboardingService;

    @InjectMocks
    private OnboardingController onboardingController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(onboardingController).build();
    }

    @Test
    void completesOnboardingForAuthenticatedUser() throws Exception {
        when(onboardingService.complete(eq(7L), any())).thenReturn(new OnboardingResponse(
                7L, 60, true, false,
                BigDecimal.valueOf(2_200_000), BigDecimal.valueOf(350_000), true));

        mockMvc.perform(post("/api/user/onboarding/me")
                        .requestAttr("userId", 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"age\":60,\"monthlyTargetLivingCost\":2200000,\"monthlyExpectedMedicalCost\":350000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.monthlyTargetLivingCost").value(2_200_000))
                .andExpect(jsonPath("$.data.monthlyExpectedMedicalCost").value(350_000))
                .andExpect(jsonPath("$.data.onboardingCompleted").value(true));

        // 요청 본문이 DTO로 실제 바인딩됐는지 직접 검증 (필드 누락/오타 시 잡아내기 위함)
        ArgumentCaptor<OnboardingRequest> captor = ArgumentCaptor.forClass(OnboardingRequest.class);
        verify(onboardingService).complete(eq(7L), captor.capture());
        OnboardingRequest bound = captor.getValue();
        assertThat(bound.age()).isEqualTo(60);
        assertThat(bound.monthlyTargetLivingCost()).isEqualByComparingTo("2200000");
        assertThat(bound.monthlyExpectedMedicalCost()).isEqualByComparingTo("350000");
    }
}
