package com.sol.user.pension.controller;

import com.sol.common.response.ApiResponse;
import com.sol.user.pension.dto.PensionDeferResponse;
import com.sol.user.pension.service.PensionDeferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "국민연금 연기 비교", description = "연기 비율·연수별 수령액 및 충당률 비교 API")
@RestController
@RequestMapping("/api/user/asset")
@RequiredArgsConstructor
public class PensionDeferController {

    private final PensionDeferService pensionDeferService;

    @Operation(summary = "국민연금 연기 비교 조회")
    @GetMapping("/pension-defer")
    public ResponseEntity<ApiResponse<PensionDeferResponse>> compare(
            @RequestAttribute("userId") Long userId,
            @RequestParam int deferRate,
            @RequestParam int deferYears) {
        return ResponseEntity.ok(
            ApiResponse.ok(pensionDeferService.compare(userId, deferRate, deferYears)));
    }
}
