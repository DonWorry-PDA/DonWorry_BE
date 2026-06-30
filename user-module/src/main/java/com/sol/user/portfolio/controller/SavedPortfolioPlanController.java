package com.sol.user.portfolio.controller;

import com.sol.common.response.ApiResponse;
import com.sol.user.portfolio.dto.SavePlanRequest;
import com.sol.user.portfolio.dto.SavePlanResponse;
import com.sol.user.portfolio.dto.SavedPlanResponse;
import com.sol.user.portfolio.service.SavedPortfolioPlanService;
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

@Tag(name = "Retirement Portfolio", description = "은퇴 포트폴리오 추천 API")
@RestController
@RequestMapping("/api/user/portfolio")
@RequiredArgsConstructor
public class SavedPortfolioPlanController {

    private final SavedPortfolioPlanService savedPortfolioPlanService;

    @Operation(summary = "설계안 저장", description = "마음에 드는 설계안 유형을 저장한다. 기존 저장 내역이 있으면 덮어쓴다.")
    @PostMapping("/saved-plan")
    public ResponseEntity<ApiResponse<SavePlanResponse>> savePlan(
            @RequestAttribute("userId") Long userId,
            @Valid @RequestBody SavePlanRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(savedPortfolioPlanService.save(userId, request)));
    }

    @Operation(summary = "저장된 설계안 조회", description = "저장된 설계안이 없으면 null을 반환한다.")
    @GetMapping("/saved-plan")
    public ResponseEntity<ApiResponse<SavedPlanResponse>> getSavedPlan(
            @RequestAttribute("userId") Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(savedPortfolioPlanService.getSaved(userId)));
    }
}
