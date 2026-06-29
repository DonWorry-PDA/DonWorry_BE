package com.sol.user.accountopen.controller;

import com.sol.common.response.ApiResponse;
import com.sol.user.accountopen.service.ShinhanCertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Account Open", description = "계좌 개설 API")
@RestController
@RequestMapping("/api/user/account-open")
@RequiredArgsConstructor
public class ShinhanCertMockController {

    private final ShinhanCertService shinhanCertService;

    @Operation(summary = "신한인증서 인증 완료 처리", description = "계좌개설 시연용 신한인증서 mock 인증 완료 상태를 저장합니다.")
    @PostMapping("/shinhan-cert/verify")
    public ResponseEntity<ApiResponse<Void>> verifyShinhanCert(
            @RequestAttribute("userId") Long userId) {
        shinhanCertService.verify(userId);
        return ResponseEntity.ok(ApiResponse.ok(null, "신한인증서 인증이 완료되었습니다."));
    }
}
