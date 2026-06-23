package com.sol.user.pension.controller;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.common.response.ApiResponse;
import com.sol.user.pension.calculator.PensionDeferCalculator;
import com.sol.user.pension.dto.PensionDeferResponse;
import com.sol.user.pension.service.PensionDeferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "국민연금 연기 비교", description = "연기 비율·연수별 수령액 및 충당률 비교 API")
@RestController
@RequestMapping("/api/user/asset")
@RequiredArgsConstructor
@Validated
public class PensionDeferController {

    private final PensionDeferService pensionDeferService;

    @Operation(summary = "국민연금 연기 비교 조회")
    @GetMapping("/pension-defer")
    public ResponseEntity<ApiResponse<PensionDeferResponse>> compare(
            @RequestAttribute("userId") Long userId,
            @RequestParam @Min(0) @Max(100) int deferRate,
            @RequestParam @Min(1) @Max(5) int deferYears) {
        if (!PensionDeferCalculator.DEFER_RATE_OPTIONS.contains(deferRate)) {
            throw new BaseException(ErrorCode.INVALID_INPUT);
        }
        return ResponseEntity.ok(
            ApiResponse.ok(pensionDeferService.compare(userId, deferRate, deferYears)));
    }
}
