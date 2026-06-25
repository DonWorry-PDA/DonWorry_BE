package com.sol.user.asset.controller;

import com.sol.common.response.ApiResponse;
import com.sol.user.asset.dto.InvestmentCheckResponse;
import com.sol.user.asset.service.InvestmentCheckService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "투자 건강검진", description = "자산의 현금흐름 역할 분해 및 개별주 성장 자산 진단 API")
@RestController
@RequestMapping("/api/user/asset")
@RequiredArgsConstructor
public class InvestmentCheckController {

    private final InvestmentCheckService investmentCheckService;

    @Operation(summary = "투자 건강검진 상세 조회",
            description = "자산을 4역할(현금흐름·성장·잠자는 돈·연금)로 분해하고, 개별주 성장 블록과 배당 전환 CTA를 반환")
    @GetMapping("/investment-check")
    public ResponseEntity<ApiResponse<InvestmentCheckResponse>> check(
            @RequestAttribute("userId") Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(investmentCheckService.check(userId)));
    }
}
