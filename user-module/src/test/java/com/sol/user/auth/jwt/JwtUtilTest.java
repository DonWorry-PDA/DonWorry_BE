package com.sol.user.auth.jwt;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    // 최소 32바이트 이상의 시크릿 키 (HS256 요구사항)
    private static final String TEST_SECRET = "test-secret-key-must-be-32-chars!!";
    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(TEST_SECRET);
    }

    @Test
    @DisplayName("토큰 생성 후 userId 추출 시 동일한 값 반환")
    void generateAndExtract_returnsSameUserId() {
        String token = jwtUtil.generateToken(42L);
        Long extracted = jwtUtil.extractUserId(token);
        assertThat(extracted).isEqualTo(42L);
    }

    @Test
    @DisplayName("생성된 토큰은 null이 아님")
    void generateToken_returnsNonNull() {
        String token = jwtUtil.generateToken(1L);
        assertThat(token).isNotBlank();
    }

    @Test
    @DisplayName("잘못된 토큰으로 userId 추출 시 JwtException 발생")
    void extractUserId_invalidToken_throwsJwtException() {
        assertThatThrownBy(() -> jwtUtil.extractUserId("invalid.token.value"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("서명이 다른 토큰으로 추출 시 JwtException 발생")
    void extractUserId_wrongSignature_throwsJwtException() {
        JwtUtil otherUtil = new JwtUtil("other-secret-key-must-be-32-chars!!");
        String tokenWithWrongSig = otherUtil.generateToken(1L);

        assertThatThrownBy(() -> jwtUtil.extractUserId(tokenWithWrongSig))
                .isInstanceOf(JwtException.class);
    }
}
