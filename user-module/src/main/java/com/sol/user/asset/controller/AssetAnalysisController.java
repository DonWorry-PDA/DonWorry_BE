package com.sol.user.asset.controller;

import com.sol.common.response.ApiResponse;
import com.sol.user.asset.dto.AssetCompositionResponse;
import com.sol.user.asset.dto.AssetIncomeResponse;
import com.sol.user.asset.dto.AssetScheduleResponse;
import com.sol.user.asset.service.AssetCompositionService;
import com.sol.user.asset.service.AssetIncomeService;
import com.sol.user.asset.service.AssetScheduleService;
import com.sol.user.pension.dto.PensionResourceResponse;
import com.sol.user.pension.service.PensionResourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Asset Analysis", description = "자산분석 API")
@RestController
@RequestMapping("/api/user/asset/analysis")
@RequiredArgsConstructor
@Validated
public class AssetAnalysisController {

    private final AssetCompositionService assetCompositionService;
    private final AssetIncomeService assetIncomeService;
    private final AssetScheduleService assetScheduleService;
    private final PensionResourceService pensionResourceService;

    @Operation(summary = "자산 구성 조회", description = "전체 자산을 카테고리/계좌/보유 상품 단위로 드릴다운")
    @GetMapping("/composition")
    public ResponseEntity<ApiResponse<AssetCompositionResponse>> getComposition(
            @RequestAttribute("userId") Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(assetCompositionService.getComposition(userId)));
    }

    @Operation(summary = "월 수입 조회", description = "보유 자산이 만드는 월 수입을 소스별로 분해")
    @GetMapping("/income")
    public ResponseEntity<ApiResponse<AssetIncomeResponse>> getIncome(
            @RequestAttribute("userId") Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(assetIncomeService.getIncome(userId)));
    }

    @Operation(summary = "다가오는 현금 일정 조회", description = "배당·이자·만기 이벤트 목록")
    @GetMapping("/schedule")
    public ResponseEntity<ApiResponse<AssetScheduleResponse>> getSchedule(
            @RequestAttribute("userId") Long userId,
            @RequestParam(value = "months", defaultValue = "3") @Min(1) @Max(12) int months) {
        return ResponseEntity.ok(ApiResponse.ok(assetScheduleService.getSchedule(userId, months)));
    }

    @Operation(summary = "연금 재원 조회", description = "은퇴 후 수령 가능한 연금 재원 전체")
    @GetMapping("/pension")
    public ResponseEntity<ApiResponse<PensionResourceResponse>> getPension(
            @RequestAttribute("userId") Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(pensionResourceService.getPension(userId)));
    }
}
