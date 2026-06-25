package com.sol.user.accountopen.service;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OtpServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @Mock
    private SmsService smsService;

    @InjectMocks
    private OtpService otpService;

    private static final Long USER_ID = 1L;
    private static final String PHONE = "010-1234-5678";
    private static final String USER_PHONE = USER_ID + ":" + PHONE;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // ── sendOtp ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("정상 전화번호로 첫 발송 시 OTP를 저장하고 SMS를 발송한다")
    void sendOtp_firstTime_savesOtpAndSendsSms() {
        when(valueOps.increment("otp:rate:" + USER_PHONE)).thenReturn(1L);

        otpService.sendOtp(PHONE, USER_ID);

        verify(redisTemplate).expire(eq("otp:rate:" + USER_PHONE), eq(5L), eq(TimeUnit.MINUTES));
        verify(valueOps).set(eq("otp:code:" + USER_PHONE), anyString(), eq(3L), eq(TimeUnit.MINUTES));
        verify(redisTemplate).delete("otp:attempts:" + USER_PHONE);
        verify(smsService).sendOtp(eq(PHONE), anyString());
    }

    @Test
    @DisplayName("잘못된 전화번호 형식이면 INVALID_PHONE 예외를 던진다")
    void sendOtp_invalidPhone_throwsInvalidPhone() {
        assertThatThrownBy(() -> otpService.sendOtp("01012345678", USER_ID))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_PHONE);
    }

    @Test
    @DisplayName("5분 내 6번째 발송 요청은 OTP_TOO_MANY_REQUESTS를 던진다")
    void sendOtp_rateLimitExceeded_throwsTooManyRequests() {
        when(valueOps.increment("otp:rate:" + USER_PHONE)).thenReturn(6L);

        assertThatThrownBy(() -> otpService.sendOtp(PHONE, USER_ID))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.OTP_TOO_MANY_REQUESTS);

        verify(smsService, never()).sendOtp(any(), any());
    }

    // ── verifyOtp ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("올바른 OTP 입력 시 verified 플래그를 Redis에 저장한다")
    void verifyOtp_correct_setsVerifiedFlag() {
        when(valueOps.increment("otp:attempts:" + USER_PHONE)).thenReturn(1L);
        when(valueOps.get("otp:code:" + USER_PHONE)).thenReturn("123456");

        otpService.verifyOtp(PHONE, "123456", USER_ID);

        verify(redisTemplate).delete("otp:code:" + USER_PHONE);
        verify(redisTemplate).delete("otp:attempts:" + USER_PHONE);
        verify(valueOps).set(eq("otp:verified:" + USER_ID), eq("Y"), eq(30L), eq(TimeUnit.MINUTES));
    }

    @Test
    @DisplayName("저장된 OTP가 없으면 OTP_EXPIRED를 던진다")
    void verifyOtp_expired_throwsOtpExpired() {
        when(valueOps.increment("otp:attempts:" + USER_PHONE)).thenReturn(1L);
        when(valueOps.get("otp:code:" + USER_PHONE)).thenReturn(null);

        assertThatThrownBy(() -> otpService.verifyOtp(PHONE, "123456", USER_ID))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.OTP_EXPIRED);
    }

    @Test
    @DisplayName("OTP 불일치 시 OTP_INVALID를 던진다")
    void verifyOtp_wrongCode_throwsOtpInvalid() {
        when(valueOps.increment("otp:attempts:" + USER_PHONE)).thenReturn(1L);
        when(valueOps.get("otp:code:" + USER_PHONE)).thenReturn("999999");

        assertThatThrownBy(() -> otpService.verifyOtp(PHONE, "123456", USER_ID))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.OTP_INVALID);
    }

    @Test
    @DisplayName("6번째 시도부터 OTP_MAX_ATTEMPTS를 던진다")
    void verifyOtp_maxAttempts_throwsOtpMaxAttempts() {
        when(valueOps.increment("otp:attempts:" + USER_PHONE)).thenReturn(6L);

        assertThatThrownBy(() -> otpService.verifyOtp(PHONE, "123456", USER_ID))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.OTP_MAX_ATTEMPTS);

        verify(valueOps, never()).get(anyString());
    }

    // ── isVerified / clearVerified ───────────────────────────────────────────────

    @Test
    @DisplayName("Redis에 'Y'가 저장되어 있으면 isVerified는 true를 반환한다")
    void isVerified_flagSet_returnsTrue() {
        when(valueOps.get("otp:verified:" + USER_ID)).thenReturn("Y");

        assertThat(otpService.isVerified(USER_ID)).isTrue();
    }

    @Test
    @DisplayName("Redis에 값이 없으면 isVerified는 false를 반환한다")
    void isVerified_noFlag_returnsFalse() {
        when(valueOps.get("otp:verified:" + USER_ID)).thenReturn(null);

        assertThat(otpService.isVerified(USER_ID)).isFalse();
    }

    @Test
    @DisplayName("clearVerified는 Redis에서 verified 키를 삭제한다")
    void clearVerified_deletesKey() {
        otpService.clearVerified(USER_ID);

        verify(redisTemplate).delete("otp:verified:" + USER_ID);
    }
}
