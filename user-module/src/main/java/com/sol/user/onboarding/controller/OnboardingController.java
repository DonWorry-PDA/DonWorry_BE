package com.sol.user.onboarding.controller;

import com.sol.common.response.ApiResponse;
import com.sol.user.onboarding.dto.OnboardingRequest;
import com.sol.user.onboarding.dto.OnboardingResponse;
import com.sol.user.onboarding.service.OnboardingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "온보딩", description = "나이·목표 생활비·예상 의료비 입력 API")
@RestController
@RequestMapping("/api/user/onboarding")
@RequiredArgsConstructor
public class OnboardingController {

    private final OnboardingService onboardingService;

    @Operation(summary = "온보딩 입력 저장",
            description = "나이/은퇴 여부/국민연금 수령 상태와 목표 생활비·예상 의료비를 저장한다. "
                    + "목표 생활비/예상 의료비 미입력 시 시연 기본값(2,200,000 / 350,000)을 사용한다.")
    @PostMapping("/me")
    public ResponseEntity<ApiResponse<OnboardingResponse>> completeMyOnboarding(
            @RequestAttribute("userId") Long userId,
            @RequestBody(required = false) OnboardingRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(onboardingService.complete(userId, request)));
    }
}
