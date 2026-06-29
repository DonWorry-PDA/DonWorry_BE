package com.sol.user.monthlysalary.controller;

import com.sol.common.response.ApiResponse;
import com.sol.user.monthlysalary.dto.SalaryPlanConfirmRequest;
import com.sol.user.monthlysalary.dto.SalaryPlanStatusResponse;
import com.sol.user.monthlysalary.service.SalaryPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Monthly Salary Plan", description = "월급 만들기 확정 plan / 현재 운용 현황 API")
@RestController
@RequestMapping("/api/user/monthly-salary/plan")
@RequiredArgsConstructor
public class SalaryPlanController {

    private final SalaryPlanService salaryPlanService;

    @Operation(summary = "월급 만들기 확정",
            description = "매수 완료 후 유저가 고른 안과 종목별 목표를 스냅샷 저장한다. 기존 확정안은 자동으로 대체된다.")
    @PostMapping
    public ResponseEntity<ApiResponse<Void>> confirm(
            @RequestAttribute("userId") Long userId,
            @Valid @RequestBody SalaryPlanConfirmRequest request) {
        salaryPlanService.confirm(userId, request);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @Operation(summary = "현재 운용 현황 조회",
            description = "ACTIVE plan이 있으면 ACTIVE plan을, 없으면 최신 plan 이력을 조회한다. plan 이력이 전혀 없으면 hasPlan=false를 내려준다.")
    @GetMapping
    public ResponseEntity<ApiResponse<SalaryPlanStatusResponse>> getStatus(
            @RequestAttribute("userId") Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(salaryPlanService.getStatus(userId)));
    }
}
