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

@Tag(name = "Life Stability", description = "Life stability API")
@RestController
@RequestMapping("/api/user/life-stability")
@RequiredArgsConstructor
public class LifeStabilityController {

    private final LifeStabilityService lifeStabilityService;

    @Operation(summary = "Get latest life stability result")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<LifeStabilityResponse>> getMyLifeStability(
            @RequestAttribute("userId") Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(lifeStabilityService.getLatest(userId)));
    }

    @Operation(summary = "Preview life stability result with sample data")
    @PostMapping("/preview")
    public ResponseEntity<ApiResponse<LifeStabilityResponse>> previewLifeStability() {
        return ResponseEntity.ok(ApiResponse.ok(lifeStabilityService.preview()));
    }
}
