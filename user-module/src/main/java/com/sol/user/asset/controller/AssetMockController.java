package com.sol.user.asset.controller;

import com.sol.common.response.ApiResponse;
import com.sol.user.asset.dto.MockAssetRequest;
import com.sol.user.asset.dto.MockAssetResponse;
import com.sol.user.asset.service.AssetMockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Assets", description = "MyData mock asset API")
@RestController
@RequestMapping({"/api/assets", "/api/user/assets"})
@RequiredArgsConstructor
public class AssetMockController {

    private final AssetMockService assetMockService;

    @Operation(summary = "Create and apply MyData mock assets")
    @PostMapping("/mock/me")
    public ResponseEntity<ApiResponse<MockAssetResponse>> createMyMockAssets(
            @RequestAttribute("userId") Long userId,
            @RequestBody MockAssetRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(assetMockService.create(
                userId,
                request == null ? null : request.mockType()
        )));
    }
}
