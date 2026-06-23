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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private User mockUser;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    private AuthService authService;
    private BCryptPasswordEncoder encoder;

    @BeforeEach
    void setUp() {
        encoder = new BCryptPasswordEncoder();
        authService = new AuthService(userRepository, jwtUtil, encoder, tokenBlacklistService);
    }

    @ParameterizedTest(name = "PIN {1} authenticates user {0}")
    @CsvSource({
            "1, 123456, false",
            "2, 654321, true",
            "3, 333333, false"
    })
    @DisplayName("PIN만으로 해당 사용자를 찾아 토큰과 온보딩 상태를 반환한다")
    void login_validPin_returnsMatchedUserToken(
            long expectedUserId,
            String pin,
            boolean onboardingCompleted
    ) {
        User user1 = userWithPin("123456");
        User user2 = userWithPin("654321");
        User user3 = userWithPin("333333");
        User expectedUser = switch ((int) expectedUserId) {
            case 1 -> user1;
            case 2 -> user2;
            case 3 -> user3;
            default -> throw new IllegalArgumentException("Unexpected test user id");
        };

        when(userRepository.findAllByPasswordIsNotNullOrderByUserIdAsc())
                .thenReturn(List.of(user1, user2, user3));
        when(expectedUser.getUserId()).thenReturn(expectedUserId);
        when(expectedUser.getOnboardingCompleted()).thenReturn(onboardingCompleted);
        when(jwtUtil.generateToken(expectedUserId)).thenReturn("token-" + expectedUserId);

        LoginResponse response = authService.login(new LoginRequest(pin));

        assertThat(response.token()).isEqualTo("token-" + expectedUserId);
        assertThat(response.onboardingCompleted()).isEqualTo(onboardingCompleted);
    }

    @Test
    @DisplayName("일치하는 PIN이 없으면 AUTH_001 예외를 발생시킨다")
    void login_unknownPin_throwsAuth001() {
        User user1 = userWithPin("123456");
        User user2 = userWithPin("654321");
        User user3 = userWithPin("333333");
        when(userRepository.findAllByPasswordIsNotNullOrderByUserIdAsc())
                .thenReturn(List.of(user1, user2, user3));

        assertThatThrownBy(() -> authService.login(new LoginRequest("999999")))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_001);

        verify(jwtUtil, never()).generateToken(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("동일한 PIN에 둘 이상의 사용자가 매칭되면 인증을 거부한다")
    void login_duplicatePin_throwsAuth001() {
        User user1 = userWithPin("123456");
        User user2 = userWithPin("123456");
        when(userRepository.findAllByPasswordIsNotNullOrderByUserIdAsc())
                .thenReturn(List.of(user1, user2));

        assertThatThrownBy(() -> authService.login(new LoginRequest("123456")))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_001);

        verify(jwtUtil, never()).generateToken(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("온보딩 완료 처리 시 사용자의 완료 메서드를 호출한다")
    void completeOnboarding_callsUserCompleteOnboarding() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));

        authService.completeOnboarding(1L);

        verify(mockUser).completeOnboarding();
    }

    @Test
    @DisplayName("존재하지 않는 사용자의 온보딩 완료 요청은 USER_NOT_FOUND를 반환한다")
    void completeOnboarding_userNotFound_throwsUserNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.completeOnboarding(99L))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("로그아웃 시 토큰을 블랙리스트에 추가한다")
    void logout_addsTokenToBlacklist() {
        Date expiry = new Date(System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000);
        when(jwtUtil.extractExpiration("token123")).thenReturn(expiry);

        authService.logout("token123");

        verify(tokenBlacklistService).add("token123", expiry);
    }

    private User userWithPin(String pin) {
        User user = mock(User.class);
        when(user.getPassword()).thenReturn(encoder.encode(pin));
        return user;
    }
}
