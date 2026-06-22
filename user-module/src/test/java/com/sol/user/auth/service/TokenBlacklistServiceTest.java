package com.sol.user.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class TokenBlacklistServiceTest {

    private TokenBlacklistService tokenBlacklistService;

    @BeforeEach
    void setUp() {
        tokenBlacklistService = new TokenBlacklistService();
    }

    @Test
    @DisplayName("블랙리스트에 추가한 토큰은 isBlacklisted가 true를 반환함")
    void add_thenIsBlacklisted_returnsTrue() {
        Date expiry = new Date(System.currentTimeMillis() + 60_000);

        tokenBlacklistService.add("token-abc", expiry);

        assertThat(tokenBlacklistService.isBlacklisted("token-abc")).isTrue();
    }

    @Test
    @DisplayName("추가하지 않은 토큰은 isBlacklisted가 false를 반환함")
    void isBlacklisted_unknownToken_returnsFalse() {
        assertThat(tokenBlacklistService.isBlacklisted("unknown-token")).isFalse();
    }

    @Test
    @DisplayName("만료된 토큰은 evict 후 블랙리스트에서 제거됨")
    void evictExpiredTokens_removesExpiredEntries() {
        Date alreadyExpired = new Date(System.currentTimeMillis() - 1);
        Date future = new Date(System.currentTimeMillis() + 60_000);
        tokenBlacklistService.add("expired-token", alreadyExpired);
        tokenBlacklistService.add("live-token", future);

        tokenBlacklistService.evictExpiredTokens();

        assertThat(tokenBlacklistService.isBlacklisted("expired-token")).isFalse();
        assertThat(tokenBlacklistService.isBlacklisted("live-token")).isTrue();
    }
}
