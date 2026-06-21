package com.sol.user.auth.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sol.user.auth.jwt.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtInterceptorTest {

    @Mock
    private JwtUtil jwtUtil;

    private JwtInterceptor jwtInterceptor;

    @BeforeEach
    void setUp() {
        jwtInterceptor = new JwtInterceptor(jwtUtil, new ObjectMapper());
    }

    @Test
    void preHandleInjectsUserIdFromBearerToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/user/life-stability/me");
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtUtil.extractUserId("valid-token")).thenReturn(42L);

        boolean allowed = jwtInterceptor.preHandle(request, response, new Object());

        assertThat(allowed).isTrue();
        assertThat(request.getAttribute("userId")).isEqualTo(42L);
    }

    @Test
    void preHandleRejectsRequestWithoutAuthorizationHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/user/life-stability/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = jwtInterceptor.preHandle(request, response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }
}
