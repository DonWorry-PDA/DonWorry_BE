package com.sol.user.monthlysalary.controller;

import com.sol.common.response.ApiResponse;
import com.sol.user.monthlysalary.dto.SalaryAssetExclusionRequest;
import com.sol.user.monthlysalary.dto.SalaryAssetListResponse;
import com.sol.user.monthlysalary.service.SalaryAssetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Monthly Salary", description = "월급 만들기 API")
@RestController
@RequestMapping("/api/user/monthly-salary")
@RequiredArgsConstructor
public class SalaryAssetController {
    private final SalaryAssetService salaryAssetService;

    @Operation(summary = "월급 재료 자산 목록 조회")
    @GetMapping("/assets")
    public ResponseEntity<ApiResponse<SalaryAssetListResponse>> getAssets(
            @RequestAttribute("userId") Long userId
    ) {
        SalaryAssetListResponse response = salaryAssetService.getAssets(userId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(summary = "제외할 자산 저장")
    @PutMapping("/assets/exclusions")
    public ResponseEntity<ApiResponse<Void>> saveExclusions(
            @RequestAttribute("userId") Long userId,
            @Valid @RequestBody SalaryAssetExclusionRequest request
    ) {
        salaryAssetService.saveExclusions(userId, request);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
