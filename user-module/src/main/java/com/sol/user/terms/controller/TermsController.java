package com.sol.user.terms.controller;

import com.sol.common.response.ApiResponse;
import com.sol.user.terms.dto.TermsConsentRequest;
import com.sol.user.terms.dto.TermsConsentResponse;
import com.sol.user.terms.dto.TermsStatusResponse;
import com.sol.user.terms.service.TermsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "약관 동의", description = "선택 동의 조회·변경 API")
@RestController
@RequestMapping("/api/user/terms")
@RequiredArgsConstructor
public class TermsController {

    private final TermsService termsService;

    @Operation(summary = "선택 동의 현황 조회",
            description = "thirdParty(개인정보 제3자 제공)·marketing(마케팅 정보 수신) 동의 현황을 반환한다.")
    @GetMapping("/optional")
    public ResponseEntity<ApiResponse<TermsStatusResponse>> getOptionalTerms(
            @RequestAttribute("userId") Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(termsService.getOptionalTerms(userId)));
    }

    @Operation(summary = "선택 동의 변경",
            description = "termId는 thirdParty 또는 marketing만 허용. 필수 항목이거나 존재하지 않는 termId는 400 반환.")
    @PatchMapping("/{termId}/consent")
    public ResponseEntity<ApiResponse<TermsConsentResponse>> updateConsent(
            @RequestAttribute("userId") Long userId,
            @PathVariable String termId,
            @RequestBody TermsConsentRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(termsService.updateConsent(userId, termId, request)));
    }
}
