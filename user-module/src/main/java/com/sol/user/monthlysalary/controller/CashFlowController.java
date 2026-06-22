package com.sol.user.monthlysalary.controller;

import com.sol.common.response.ApiResponse;
import com.sol.user.monthlysalary.dto.CashFlowDiagnosisResponse;
import com.sol.user.monthlysalary.service.CashFlowDiagnosisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Monthly Salary", description = "월급 만들기 API")
@RestController
@RequestMapping("/api/user/monthly-salary")
@RequiredArgsConstructor
public class CashFlowController {

    private final CashFlowDiagnosisService cashFlowDiagnosisService;

    @Operation(summary = "현금흐름 진단", description = "국민연금·배당 등 현재 월 현금흐름과 목표 생활비 대비 부족액을 진단")
    @GetMapping("/cash-flow")
    public ResponseEntity<ApiResponse<CashFlowDiagnosisResponse>> diagnose(
            @RequestAttribute("userId") Long userId
    ) {
        CashFlowDiagnosisResponse response = cashFlowDiagnosisService.diagnose(userId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
