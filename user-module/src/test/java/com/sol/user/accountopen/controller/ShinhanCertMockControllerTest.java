package com.sol.user.accountopen.controller;

import com.sol.common.exception.GlobalExceptionHandler;
import com.sol.user.accountopen.service.ShinhanCertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ShinhanCertMockControllerTest {

    @Mock
    private ShinhanCertService shinhanCertService;

    @InjectMocks
    private ShinhanCertMockController shinhanCertMockController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(shinhanCertMockController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("신한인증서 인증 완료 요청 시 200을 반환한다")
    void verifyShinhanCert_ok() throws Exception {
        doNothing().when(shinhanCertService).verify(1L);

        mockMvc.perform(post("/api/user/account-open/shinhan-cert/verify")
                        .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));

        verify(shinhanCertService).verify(1L);
    }
}
