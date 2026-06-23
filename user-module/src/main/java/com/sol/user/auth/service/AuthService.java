package com.sol.user.auth.service;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.user.auth.dto.LoginRequest;
import com.sol.user.auth.dto.LoginResponse;
import com.sol.user.auth.jwt.JwtUtil;
import com.sol.user.user.entity.User;
import com.sol.user.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder;
    private final TokenBlacklistService tokenBlacklistService;

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        // BCrypt hashes are salted, so the raw PIN cannot be used in a direct equality query.
        // Reject both no-match and duplicate-match cases to avoid authenticating an ambiguous user.
        List<User> matchedUsers = userRepository.findAllByPasswordIsNotNullOrderByUserIdAsc().stream()
                .filter(user -> passwordEncoder.matches(request.pin(), user.getPassword()))
                .limit(2)
                .toList();

        if (matchedUsers.size() != 1) {
            throw new BaseException(ErrorCode.AUTH_001);
        }

        User user = matchedUsers.get(0);
        String token = jwtUtil.generateToken(user.getUserId());
        return new LoginResponse(token, user.getOnboardingCompleted());
    }

    @Transactional
    public void completeOnboarding(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));
        user.completeOnboarding();
    }

    public void logout(String token) {
        tokenBlacklistService.add(token, jwtUtil.extractExpiration(token));
    }
}
