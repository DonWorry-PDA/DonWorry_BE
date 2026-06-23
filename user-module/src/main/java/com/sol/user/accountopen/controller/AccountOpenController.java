package com.sol.user.accountopen.controller;

import com.sol.common.response.ApiResponse;
import com.sol.user.accountopen.dto.*;
import com.sol.user.accountopen.service.AccountOpenService;
import com.sol.user.accountopen.service.OtpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Account Open", description = "계좌 개설 API")
@RestController
@RequestMapping("/api/user/account-open")
@RequiredArgsConstructor
public class AccountOpenController {

    private final AccountOpenService accountOpenService;
    private final OtpService otpService;

    @Operation(summary = "약관 동의 제출")
    @PostMapping("/terms")
    public ResponseEntity<ApiResponse<Void>> agreeTerms(
            @RequestAttribute("userId") Long userId,
            @Valid @RequestBody TermsRequest request) {
        accountOpenService.validateTerms(request.getAgreedTermIds());
        return ResponseEntity.ok(ApiResponse.ok(null, "약관 동의가 완료되었습니다."));
    }

    @Operation(summary = "본인 인증 화면 — 사용자 정보 조회")
    @GetMapping("/identity")
    public ResponseEntity<ApiResponse<IdentityResponse>> getIdentity(
            @RequestAttribute("userId") Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(accountOpenService.getIdentity(userId)));
    }

    @Operation(summary = "인증번호 발송")
    @PostMapping("/otp/send")
    public ResponseEntity<ApiResponse<Void>> sendOtp(
            @RequestAttribute("userId") Long userId,
            @Valid @RequestBody OtpSendRequest request) {
        otpService.sendOtp(request.getPhone(), userId);
        return ResponseEntity.ok(ApiResponse.ok(null, "인증번호가 발송되었습니다."));
    }

    @Operation(summary = "인증번호 확인")
    @PostMapping("/otp/verify")
    public ResponseEntity<ApiResponse<Void>> verifyOtp(
            @RequestAttribute("userId") Long userId,
            @Valid @RequestBody OtpVerifyRequest request) {
        otpService.verifyOtp(request.getPhone(), request.getOtp(), userId);
        return ResponseEntity.ok(ApiResponse.ok(null, "인증이 완료되었습니다."));
    }

    @Operation(summary = "계좌 개설")
    @PostMapping
    public ResponseEntity<ApiResponse<AccountOpenResponse>> openAccount(
            @RequestAttribute("userId") Long userId,
            @Valid @RequestBody AccountOpenRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                accountOpenService.openAccount(userId, request),
                "계좌가 개설되었습니다."
        ));
    }
}
