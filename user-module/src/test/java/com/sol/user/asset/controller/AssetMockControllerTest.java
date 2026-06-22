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
class AssetMockControllerTest {

    @Mock
    private AssetMockService assetMockService;

    @InjectMocks
    private AssetMockController assetMockController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(assetMockController).build();
    }

    @Test
    void createMyMockAssetsUsesApiSpecPathAndAuthenticatedUser() throws Exception {
        MockAssetResponse response = new MockAssetResponse(
                MockType.NEED_COMPLEMENT,
                LocalDateTime.of(2026, 6, 22, 9, 41),
                new AssetSummaryResponse(
                        BigDecimal.valueOf(208_000_000),
                        BigDecimal.valueOf(30_000_000),
                        BigDecimal.valueOf(178_000_000),
                        BigDecimal.valueOf(2_200_000),
                        BigDecimal.valueOf(1_298_000),
                        BigDecimal.valueOf(-902_000),
                        BigDecimal.valueOf(59),
                        List.of()
                ),
                null,
                new MockGeneratedCounts(6, 5, 3, 1, 3, 7)
        );
        when(assetMockService.create(7L, MockType.NEED_COMPLEMENT)).thenReturn(response);

        mockMvc.perform(post("/api/assets/mock/me")
                        .requestAttr("userId", 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mockType\":\"NEED_COMPLEMENT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.mockType").value("NEED_COMPLEMENT"))
                .andExpect(jsonPath("$.data.assetSummary.cashflowCoverageRate").value(59))
                .andExpect(jsonPath("$.data.generatedCounts.cashflowEvents").value(7));

        verify(assetMockService).create(7L, MockType.NEED_COMPLEMENT);
    }
}
