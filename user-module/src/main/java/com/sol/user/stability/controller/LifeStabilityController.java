package com.sol.user.stability.controller;

import com.sol.common.response.ApiResponse;
import com.sol.user.stability.dto.LifeStabilityResponse;
import com.sol.user.stability.service.LifeStabilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "생활 안정도", description = "생활 안정도 조회 및 재계산 API")
@RestController
@RequestMapping("/api/user/life-stability")
@RequiredArgsConstructor
public class LifeStabilityController {

    private final LifeStabilityService lifeStabilityService;

    @Operation(summary = "최근 생활 안정도 결과 조회")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<LifeStabilityResponse>> getMyLifeStability(
            @RequestAttribute("userId") Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(lifeStabilityService.getLatest(userId)));
    }

    @Operation(summary = "저장된 사용자 데이터로 생활 안정도 재계산")
    @PostMapping("/me/recalculate")
    public ResponseEntity<ApiResponse<LifeStabilityResponse>> recalculateMyLifeStability(
            @RequestAttribute("userId") Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(lifeStabilityService.recalculateFromUserData(userId)));
    }

    @Operation(summary = "샘플 데이터로 생활 안정도 결과 미리보기")
    @PostMapping("/preview")
    public ResponseEntity<ApiResponse<LifeStabilityResponse>> previewLifeStability() {
        return ResponseEntity.ok(ApiResponse.ok(lifeStabilityService.preview()));
    }
}
