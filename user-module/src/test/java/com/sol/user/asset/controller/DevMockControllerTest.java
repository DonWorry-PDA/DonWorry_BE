package com.sol.user.asset.controller;

import com.sol.user.asset.dto.AssetSummaryResponse;
import com.sol.user.asset.dto.MockAssetResponse;
import com.sol.user.asset.dto.MockGeneratedCounts;
import com.sol.user.asset.service.AssetMockService;
import com.sol.user.asset.type.MockType;
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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DevMockControllerTest {

    @Mock
    private AssetMockService assetMockService;

    @InjectMocks
    private DevMockController devMockController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(devMockController).build();
    }

    @Test
    void seedAssignsScenarioToTargetUser() throws Exception {
        MockAssetResponse response = new MockAssetResponse(
                MockType.NEED_COMPLEMENT,
                LocalDateTime.of(2026, 6, 22, 9, 41),
                new AssetSummaryResponse(
                        BigDecimal.valueOf(208_000_000),
                        BigDecimal.valueOf(30_000_000),
                        BigDecimal.valueOf(178_000_000),
                        List.of()
                ),
                new MockGeneratedCounts(6, 5, 3, 1, 3, 7)
        );
        when(assetMockService.seed(2L, MockType.NEED_COMPLEMENT)).thenReturn(response);

        mockMvc.perform(post("/api/dev/mydata/mock-seed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":2,\"scenario\":\"NEED_COMPLEMENT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.mockType").value("NEED_COMPLEMENT"))
                .andExpect(jsonPath("$.data.assetSummary.netAsset").value(178_000_000));

        verify(assetMockService).seed(2L, MockType.NEED_COMPLEMENT);
    }
}
