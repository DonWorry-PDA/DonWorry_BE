package com.sol.user.report.controller;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.common.response.ApiResponse;
import com.sol.user.report.dto.MonthlyReportResponse;
import com.sol.user.report.service.MonthlyReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;

@Tag(name = "월간 리포트", description = "자산 변화·배당·소비·다음 달 미리보기를 담은 월간 리포트 API")
@RestController
@RequestMapping("/api/user/report")
@RequiredArgsConstructor
public class MonthlyReportController {

    private final MonthlyReportService monthlyReportService;

    @Operation(
            summary = "월간 리포트 조회",
            description = "month 파라미터(yyyy-MM)로 조회할 달을 지정한다. 생략 시 현재 달로 조회한다."
    )
    @GetMapping("/monthly")
    public ResponseEntity<ApiResponse<MonthlyReportResponse>> getMonthlyReport(
            @RequestAttribute("userId") Long userId,
            @Parameter(description = "조회 연월 (예: 2026-06). 생략하면 현재 달.")
            @RequestParam(required = false) String month) {
        YearMonth ym;
        try {
            ym = (month != null && !month.isBlank()) ? YearMonth.parse(month) : YearMonth.now();
        } catch (DateTimeParseException e) {
            throw new BaseException(ErrorCode.INVALID_INPUT);
        }
        return ResponseEntity.ok(ApiResponse.ok(monthlyReportService.getMonthlyReport(userId, ym)));
    }
}
