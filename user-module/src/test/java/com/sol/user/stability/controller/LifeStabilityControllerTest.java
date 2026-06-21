package com.sol.user.stability.controller;

import com.sol.user.stability.dto.LifeStabilityResponse;
import com.sol.user.stability.service.LifeStabilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class LifeStabilityControllerTest {

    @Mock
    private LifeStabilityService lifeStabilityService;

    @InjectMocks
    private LifeStabilityController lifeStabilityController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(lifeStabilityController).build();
    }

    @Test
    void getMyLifeStabilityReturnsWrappedResponse() throws Exception {
        LifeStabilityResponse response = createResponse();
        when(lifeStabilityService.getLatest(1L)).thenReturn(response);

        mockMvc.perform(get("/api/user/life-stability/me")
                        .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("성공"))
                .andExpect(jsonPath("$.data.grade").value("CAUTION"));

        verify(lifeStabilityService).getLatest(1L);
    }

    @Test
    void previewLifeStabilityReturnsWrappedResponse() throws Exception {
        LifeStabilityResponse response = createResponse();
        when(lifeStabilityService.preview()).thenReturn(response);

        mockMvc.perform(post("/api/user/life-stability/preview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("성공"))
                .andExpect(jsonPath("$.data.grade").value("CAUTION"));

        verify(lifeStabilityService).preview();
    }

    private LifeStabilityResponse createResponse() {
        return LifeStabilityResponse.builder()
                .grade("CAUTION")
                .gradeLabel("주의")
                .summaryMessage("생활 안정도 점검이 필요해요.")
                .improvementMessages(List.of())
                .build();
    }
}
