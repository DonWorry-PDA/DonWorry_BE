package com.sol.user.auth.controller;

import com.sol.common.response.ApiResponse;
import com.sol.user.auth.dto.LoginRequest;
import com.sol.user.auth.dto.LoginResponse;
import com.sol.user.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth", description = "인증 API")
@RestController
@RequestMapping("/api/user/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "PIN 로그인")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.login(request)));
    }

    @Operation(summary = "온보딩 완료 처리")
    @PatchMapping("/onboarding/complete")
    public ResponseEntity<ApiResponse<Void>> completeOnboarding(@RequestAttribute("userId") Long userId) {
        authService.completeOnboarding(userId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
