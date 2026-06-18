package com.sol.user.auth.service;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.user.auth.dto.LoginRequest;
import com.sol.user.auth.dto.LoginResponse;
import com.sol.user.auth.jwt.JwtUtil;
import com.sol.user.user.entity.User;
import com.sol.user.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private JwtUtil jwtUtil;
    @Mock private User mockUser;

    private AuthService authService;
    private BCryptPasswordEncoder encoder;

    @BeforeEach
    void setUp() {
        encoder = new BCryptPasswordEncoder();
        authService = new AuthService(userRepository, jwtUtil, encoder);
    }

    @Test
    @DisplayName("올바른 PIN 입력 시 토큰과 onboardingCompleted 반환")
    void login_validPin_returnsTokenAndOnboardingStatus() {
        String hashedPin = encoder.encode("123456");
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(mockUser.getPassword()).thenReturn(hashedPin);
        when(mockUser.getOnboardingCompleted()).thenReturn(false);
        when(mockUser.getUserId()).thenReturn(1L);
        when(jwtUtil.generateToken(1L)).thenReturn("token123");

        LoginResponse response = authService.login(new LoginRequest(1L, "123456"));

        assertThat(response.token()).isEqualTo("token123");
        assertThat(response.onboardingCompleted()).isFalse();
    }

    @Test
    @DisplayName("온보딩 완료 사용자는 onboardingCompleted=true 반환")
    void login_onboardedUser_returnsOnboardingCompletedTrue() {
        String hashedPin = encoder.encode("123456");
        when(userRepository.findById(2L)).thenReturn(Optional.of(mockUser));
        when(mockUser.getPassword()).thenReturn(hashedPin);
        when(mockUser.getOnboardingCompleted()).thenReturn(true);
        when(mockUser.getUserId()).thenReturn(2L);
        when(jwtUtil.generateToken(2L)).thenReturn("token456");

        LoginResponse response = authService.login(new LoginRequest(2L, "123456"));

        assertThat(response.onboardingCompleted()).isTrue();
    }

    @Test
    @DisplayName("잘못된 PIN 입력 시 AUTH_001 예외 발생")
    void login_wrongPin_throwsAuth001() {
        String hashedPin = encoder.encode("123456");
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(mockUser.getPassword()).thenReturn(hashedPin);

        assertThatThrownBy(() -> authService.login(new LoginRequest(1L, "wrong")))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_001);
    }

    @Test
    @DisplayName("존재하지 않는 userId 입력 시 AUTH_001 예외 발생")
    void login_userNotFound_throwsAuth001() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest(99L, "123456")))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_001);
    }

    @Test
    @DisplayName("completeOnboarding 호출 시 user.completeOnboarding()이 호출됨")
    void completeOnboarding_callsUserCompleteOnboarding() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));

        authService.completeOnboarding(1L);

        verify(mockUser).completeOnboarding();
    }

    @Test
    @DisplayName("존재하지 않는 userId로 completeOnboarding 호출 시 USER_NOT_FOUND 예외")
    void completeOnboarding_userNotFound_throwsUserNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.completeOnboarding(99L))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }
}
