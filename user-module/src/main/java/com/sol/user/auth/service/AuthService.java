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

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new BaseException(ErrorCode.AUTH_001));

        if (!passwordEncoder.matches(request.pin(), user.getPassword())) {
            throw new BaseException(ErrorCode.AUTH_001);
        }

        String token = jwtUtil.generateToken(user.getUserId());
        return new LoginResponse(token, user.getOnboardingCompleted());
    }

    @Transactional
    public void completeOnboarding(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));
        user.completeOnboarding();
    }
}
