package com.sol.user.portfolio.controller;

import com.sol.common.response.ApiResponse;
import com.sol.user.portfolio.dto.OperationGradeResponse;
import com.sol.user.portfolio.service.OperationGradeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Retirement Portfolio", description = "은퇴 포트폴리오 추천 API")
@RestController
@RequestMapping("/api/user/portfolio")
@RequiredArgsConstructor
public class OperationGradeController {

    private final OperationGradeService operationGradeService;

    @Operation(summary = "운용 등급 산출 (STEP1~4)")
    @GetMapping("/grade")
    public ResponseEntity<ApiResponse<OperationGradeResponse>> getOperationGrade(
            @RequestAttribute("userId") Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(operationGradeService.calculate(userId)));
    }
}
