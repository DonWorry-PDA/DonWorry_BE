package com.sol.user.accountopen.controller;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.common.exception.GlobalExceptionHandler;
import com.sol.user.accountopen.dto.AccountOpenResponse;
import com.sol.user.accountopen.dto.IdentityResponse;
import com.sol.user.accountopen.service.AccountOpenService;
import com.sol.user.accountopen.service.OtpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AccountOpenControllerTest {

    @Mock
    private AccountOpenService accountOpenService;

    @Mock
    private OtpService otpService;

    @InjectMocks
    private AccountOpenController accountOpenController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(accountOpenController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ── 약관 동의 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("필수 약관 4개를 모두 동의하면 200을 반환한다")
    void agreeTerms_allRequired_ok() throws Exception {
        doNothing().when(accountOpenService).validateTerms(any());

        mockMvc.perform(post("/api/user/account-open/terms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agreedTermIds\":[\"account\",\"deposit\",\"account-privacy\",\"account-third-party\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("필수 약관 미동의 시 400을 반환한다")
    void agreeTerms_missing_badRequest() throws Exception {
        doThrow(new BaseException(ErrorCode.TERMS_NOT_AGREED))
                .when(accountOpenService).validateTerms(any());

        mockMvc.perform(post("/api/user/account-open/terms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agreedTermIds\":[\"account\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TERMS_NOT_AGREED"));
    }

    // ── 본인 정보 조회 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("identity 조회 시 이름·마스킹된 주민번호·전화번호를 반환한다")
    void getIdentity_returnsUserInfo() throws Exception {
        IdentityResponse response = IdentityResponse.builder()
                .name("홍길동")
                .idNumberMasked("901010-●●●●●●●")
                .phone("010-1234-5678")
                .build();
        when(accountOpenService.getIdentity(1L)).thenReturn(response);

        mockMvc.perform(get("/api/user/account-open/identity")
                        .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("홍길동"))
                .andExpect(jsonPath("$.data.idNumberMasked").value("901010-●●●●●●●"))
                .andExpect(jsonPath("$.data.phone").value("010-1234-5678"));
    }

    // ── OTP 발송 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("올바른 전화번호로 OTP 발송 요청 시 200을 반환한다")
    void sendOtp_validPhone_ok() throws Exception {
        doNothing().when(otpService).sendOtp(eq("010-1234-5678"), eq(1L));

        mockMvc.perform(post("/api/user/account-open/otp/send")
                        .requestAttr("userId", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"010-1234-5678\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("전화번호 누락 시 400을 반환한다")
    void sendOtp_missingPhone_badRequest() throws Exception {
        mockMvc.perform(post("/api/user/account-open/otp/send")
                        .requestAttr("userId", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));
    }

    @Test
    @DisplayName("재발송 횟수 초과 시 429를 반환한다")
    void sendOtp_rateLimitExceeded_tooManyRequests() throws Exception {
        doThrow(new BaseException(ErrorCode.OTP_TOO_MANY_REQUESTS))
                .when(otpService).sendOtp(any(), any());

        mockMvc.perform(post("/api/user/account-open/otp/send")
                        .requestAttr("userId", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"010-1234-5678\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("OTP_TOO_MANY_REQUESTS"));
    }

    // ── OTP 확인 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("올바른 OTP 입력 시 200을 반환한다")
    void verifyOtp_correct_ok() throws Exception {
        doNothing().when(otpService).verifyOtp(eq("010-1234-5678"), eq("123456"), eq(1L));

        mockMvc.perform(post("/api/user/account-open/otp/verify")
                        .requestAttr("userId", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"010-1234-5678\",\"otp\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("OTP 형식이 6자리 숫자가 아니면 400을 반환한다")
    void verifyOtp_invalidFormat_badRequest() throws Exception {
        mockMvc.perform(post("/api/user/account-open/otp/verify")
                        .requestAttr("userId", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"010-1234-5678\",\"otp\":\"12345\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));
    }

    @Test
    @DisplayName("OTP 불일치 시 400을 반환한다")
    void verifyOtp_wrong_badRequest() throws Exception {
        doThrow(new BaseException(ErrorCode.OTP_INVALID))
                .when(otpService).verifyOtp(any(), any(), any());

        mockMvc.perform(post("/api/user/account-open/otp/verify")
                        .requestAttr("userId", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"010-1234-5678\",\"otp\":\"000000\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OTP_INVALID"));
    }

    @Test
    @DisplayName("OTP 만료 시 400을 반환한다")
    void verifyOtp_expired_badRequest() throws Exception {
        doThrow(new BaseException(ErrorCode.OTP_EXPIRED))
                .when(otpService).verifyOtp(any(), any(), any());

        mockMvc.perform(post("/api/user/account-open/otp/verify")
                        .requestAttr("userId", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"010-1234-5678\",\"otp\":\"123456\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OTP_EXPIRED"));
    }

    // ── 계좌 개설 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("OTP 인증 완료 후 계좌 개설 시 계좌번호와 개설일을 반환한다")
    void openAccount_verified_returnsAccountInfo() throws Exception {
        AccountOpenResponse response = AccountOpenResponse.builder()
                .accountNumber("110-123-456789")
                .openedAt("2026.06.24")
                .build();
        when(accountOpenService.openAccount(eq(1L), any())).thenReturn(response);

        mockMvc.perform(post("/api/user/account-open")
                        .requestAttr("userId", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agreedTermIds\":[\"account\",\"deposit\",\"account-privacy\",\"account-third-party\"],\"phone\":\"010-1234-5678\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountNumber").value("110-123-456789"))
                .andExpect(jsonPath("$.data.openedAt").value("2026.06.24"));
    }

    @Test
    @DisplayName("OTP 미인증 상태에서 계좌 개설 시 403을 반환한다")
    void openAccount_notVerified_forbidden() throws Exception {
        doThrow(new BaseException(ErrorCode.OTP_NOT_VERIFIED))
                .when(accountOpenService).openAccount(any(), any());

        mockMvc.perform(post("/api/user/account-open")
                        .requestAttr("userId", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agreedTermIds\":[\"account\",\"deposit\",\"account-privacy\",\"account-third-party\"],\"phone\":\"010-1234-5678\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("OTP_NOT_VERIFIED"));
    }

    @Test
    @DisplayName("계좌가 이미 존재하면 409를 반환한다")
    void openAccount_alreadyExists_conflict() throws Exception {
        doThrow(new BaseException(ErrorCode.ACCOUNT_ALREADY_EXISTS))
                .when(accountOpenService).openAccount(any(), any());

        mockMvc.perform(post("/api/user/account-open")
                        .requestAttr("userId", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agreedTermIds\":[\"account\",\"deposit\",\"account-privacy\",\"account-third-party\"],\"phone\":\"010-1234-5678\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_ALREADY_EXISTS"));
    }
}
