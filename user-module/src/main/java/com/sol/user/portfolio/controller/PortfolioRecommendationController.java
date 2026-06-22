package com.sol.user.portfolio.controller;

import com.sol.common.response.ApiResponse;
import com.sol.user.portfolio.dto.RecommendationResponse;
import com.sol.user.portfolio.service.PortfolioRecommendationService;
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
public class PortfolioRecommendationController {

    private final PortfolioRecommendationService portfolioRecommendationService;

    @Operation(summary = "은퇴 포트폴리오 추천 (STEP1~6: 배분 3안 + α충족률 + 소진)")
    @GetMapping("/recommendation")
    public ResponseEntity<ApiResponse<RecommendationResponse>> getRecommendation(
            @RequestAttribute("userId") Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(portfolioRecommendationService.recommend(userId)));
    }
}
