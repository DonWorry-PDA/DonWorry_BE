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
class MydataMockControllerTest {

    @Mock
    private AssetMockService assetMockService;

    @InjectMocks
    private MydataMockController mydataMockController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(mydataMockController).build();
    }

    @Test
    void connectSyncsAuthenticatedUserWithoutExposingScenario() throws Exception {
        when(assetMockService.sync(7L)).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/user/mydata/mock/connect")
                        .requestAttr("userId", 7L)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.connectedInstitutions").value(6))
                .andExpect(jsonPath("$.data.message").value("마이데이터 정보를 불러왔습니다."))
                .andExpect(jsonPath("$.data.assetSummary.netAsset").value(178_000_000))
                .andExpect(jsonPath("$.data.mockType").doesNotExist());

        verify(assetMockService).sync(7L);
    }

    @Test
    void syncReSyncsAuthenticatedUser() throws Exception {
        when(assetMockService.sync(7L)).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/user/mydata/mock/sync")
                        .requestAttr("userId", 7L)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("마이데이터 정보를 다시 동기화했습니다."));

        verify(assetMockService).sync(7L);
    }

    private MockAssetResponse sampleResponse() {
        return new MockAssetResponse(
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
    }
}
