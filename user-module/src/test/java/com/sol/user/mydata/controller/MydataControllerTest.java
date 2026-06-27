package com.sol.user.mydata.controller;

import com.sol.user.mydata.dto.ConnectedInstitutionCountResponse;
import com.sol.user.mydata.dto.MydataAccountResponse;
import com.sol.user.mydata.dto.MydataCashFlowResponse;
import com.sol.user.mydata.dto.MydataHoldingResponse;
import com.sol.user.mydata.dto.MydataPensionResponse;
import com.sol.user.mydata.dto.MydataTradeResponse;
import com.sol.user.mydata.dto.MydataTransactionsResponse;
import com.sol.user.mydata.service.InstitutionService;
import com.sol.user.mydata.service.MydataQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MydataControllerTest {

    @Mock
    private MydataQueryService mydataQueryService;

    @Mock
    private InstitutionService institutionService;

    @InjectMocks
    private MydataController mydataController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(mydataController).build();
    }

    @Test
    void getAccountsReturnsWrappedAccounts() throws Exception {
        when(mydataQueryService.getAccounts(1L)).thenReturn(List.of(
                new MydataAccountResponse(
                        10L,
                        "CMA",
                        "신한은행",
                        "110-123-456789",
                        BigDecimal.valueOf(1_000_000),
                        true
                )
        ));

        mockMvc.perform(get("/api/user/mydata/accounts")
                        .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data[0].accountId").value(10))
                .andExpect(jsonPath("$.data[0].accountType").value("CMA"));

        verify(mydataQueryService).getAccounts(1L);
    }

    @Test
    void getTransactionsPassesInclusiveDateRange() throws Exception {
        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 6, 30);
        MydataTransactionsResponse response = new MydataTransactionsResponse(
                List.of(new MydataCashFlowResponse(
                        20L,
                        LocalDate.of(2026, 6, 25),
                        "DIVIDEND",
                        "ETF 배당금",
                        BigDecimal.valueOf(34_200),
                        "INCOME",
                        "SCHEDULED",
                        true,
                        "MYDATA"
                )),
                List.of(new MydataTradeResponse(
                        30L,
                        10L,
                        "BUY",
                        LocalDateTime.of(2026, 6, 10, 9, 30),
                        BigDecimal.ONE,
                        BigDecimal.valueOf(50_000),
                        BigDecimal.valueOf(50_000),
                        BigDecimal.valueOf(100),
                        100L
                ))
        );
        when(mydataQueryService.getTransactions(1L, from, to)).thenReturn(response);

        mockMvc.perform(get("/api/user/mydata/transactions")
                        .requestAttr("userId", 1L)
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cashFlows[0].eventType").value("DIVIDEND"))
                .andExpect(jsonPath("$.data.trades[0].tradeType").value("BUY"));

        verify(mydataQueryService).getTransactions(1L, from, to);
    }

    @Test
    void getHoldingsReturnsWrappedHoldings() throws Exception {
        when(mydataQueryService.getHoldings(1L)).thenReturn(List.of(
                new MydataHoldingResponse(
                        40L,
                        10L,
                        100L,
                        BigDecimal.valueOf(500_000),
                        BigDecimal.TEN,
                        "45000",
                        "50000",
                        false
                )
        ));

        mockMvc.perform(get("/api/user/mydata/holdings")
                        .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].holdingId").value(40))
                .andExpect(jsonPath("$.data[0].productId").value(100));

        verify(mydataQueryService).getHoldings(1L);
    }

    @Test
    void getConnectedInstitutionCountReturnsWrappedCount() throws Exception {
        when(institutionService.getConnectedInstitutionCount(1L))
                .thenReturn(new ConnectedInstitutionCountResponse(5));

        mockMvc.perform(get("/api/user/mydata/institutions/connected-count")
                        .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.connectedInstitutionCount").value(5));

        verify(institutionService).getConnectedInstitutionCount(1L);
    }

    @Test
    void getPensionsReturnsWrappedPensions() throws Exception {
        when(mydataQueryService.getPensions(1L)).thenReturn(List.of(
                new MydataPensionResponse(
                        50L,
                        "NATIONAL",
                        BigDecimal.valueOf(1_150_000),
                        false,
                        65
                )
        ));

        mockMvc.perform(get("/api/user/mydata/pensions")
                        .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].pensionType").value("NATIONAL"))
                .andExpect(jsonPath("$.data[0].startAge").value(65));

        verify(mydataQueryService).getPensions(1L);
    }
}
