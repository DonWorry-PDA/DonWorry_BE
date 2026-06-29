package com.sol.user.accountopen.service;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShinhanCertServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @InjectMocks
    private ShinhanCertService shinhanCertService;

    private static final Long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    @DisplayName("신한인증서 인증 완료 상태를 Redis에 저장한다")
    void verify_setsVerifiedFlag() {
        shinhanCertService.verify(USER_ID);

        verify(valueOps).set(eq("shinhan-cert:verified:" + USER_ID), eq("Y"), eq(30L), eq(TimeUnit.MINUTES));
    }

    @Test
    @DisplayName("Redis에 인증 완료 값이 있으면 true를 반환한다")
    void isVerified_flagSet_returnsTrue() {
        when(valueOps.get("shinhan-cert:verified:" + USER_ID)).thenReturn("Y");

        assertThat(shinhanCertService.isVerified(USER_ID)).isTrue();
    }

    @Test
    @DisplayName("Redis에 인증 완료 값이 없으면 false를 반환한다")
    void isVerified_noFlag_returnsFalse() {
        when(valueOps.get("shinhan-cert:verified:" + USER_ID)).thenReturn(null);

        assertThat(shinhanCertService.isVerified(USER_ID)).isFalse();
    }

    @Test
    @DisplayName("신한인증서 인증 완료 상태를 Redis에서 삭제한다")
    void clearVerified_deletesKey() {
        shinhanCertService.clearVerified(USER_ID);

        verify(redisTemplate).delete("shinhan-cert:verified:" + USER_ID);
    }
}
