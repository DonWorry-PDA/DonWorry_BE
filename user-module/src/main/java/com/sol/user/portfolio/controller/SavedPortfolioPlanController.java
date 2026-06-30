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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Retirement Portfolio", description = "은퇴 포트폴리오 추천 API")
@RestController
@RequestMapping("/api/user/portfolio")
@RequiredArgsConstructor
public class SavedPortfolioPlanController {

    private final SavedPortfolioPlanService savedPortfolioPlanService;

    @Operation(summary = "설계안 저장", description = "마음에 드는 설계안을 저장한다. 여러 건 저장 가능.")
    @PostMapping("/saved-plan")
    public ResponseEntity<ApiResponse<SavePlanResponse>> savePlan(
            @RequestAttribute("userId") Long userId,
            @Valid @RequestBody SavePlanRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(savedPortfolioPlanService.save(userId, request)));
    }

    @Operation(summary = "저장된 설계안 목록 조회", description = "유저가 저장한 설계안 전체 목록을 반환한다.")
    @GetMapping("/saved-plan")
    public ResponseEntity<ApiResponse<List<SavedPlanResponse>>> getSavedPlanList(
            @RequestAttribute("userId") Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(savedPortfolioPlanService.getSavedList(userId)));
    }

    @Operation(summary = "저장된 설계안 삭제", description = "planId에 해당하는 저장된 설계안을 삭제한다.")
    @DeleteMapping("/saved-plan/{planId}")
    public ResponseEntity<ApiResponse<Void>> deleteSavedPlan(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long planId) {
        savedPortfolioPlanService.delete(userId, planId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
