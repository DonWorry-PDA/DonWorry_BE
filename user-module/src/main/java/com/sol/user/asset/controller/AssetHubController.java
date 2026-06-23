package com.sol.user.asset.controller;

import com.sol.common.response.ApiResponse;
import com.sol.user.asset.dto.AssetHubResponse;
import com.sol.user.asset.service.AssetHubService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Asset Hub", description = "자산관리 메인 허브 API")
@RestController
@RequestMapping("/api/user/asset")
@RequiredArgsConstructor
public class AssetHubController {

    private final AssetHubService assetHubService;

    @Operation(summary = "자산관리 메인 허브 집계 조회",
            description = "총 보유금·자산 분포·당월 수입/지출·관리 메뉴 6개 미리보기 수치를 단일 응답으로 반환")
    @GetMapping("/hub")
    public ResponseEntity<ApiResponse<AssetHubResponse>> getHub(
            @RequestAttribute("userId") Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(assetHubService.getHub(userId)));
    }
}
