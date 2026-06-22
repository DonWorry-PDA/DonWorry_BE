package com.sol.user.asset.controller;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.common.response.ApiResponse;
import com.sol.user.asset.dto.DevMockSeedRequest;
import com.sol.user.asset.dto.MockAssetResponse;
import com.sol.user.asset.service.AssetMockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "개발용 목업", description = "시연/테스트 데이터 시드 API (프론트 비노출)")
@RestController
@RequestMapping("/api/dev/mydata")
@RequiredArgsConstructor
public class DevMockController {

    private final AssetMockService assetMockService;

    @Operation(summary = "사용자별 목업 시나리오 강제 배정",
            description = "기존 목업 원천 데이터를 모두 지우고 지정한 시나리오로 새로 생성한다.")
    @PostMapping("/mock-seed")
    public ResponseEntity<ApiResponse<MockAssetResponse>> seed(
            @RequestBody DevMockSeedRequest request) {
        if (request == null || request.userId() == null) {
            throw new BaseException(ErrorCode.INVALID_INPUT);
        }
        return ResponseEntity.ok(ApiResponse.ok(
                assetMockService.seed(request.userId(), request.scenario())
        ));
    }
}
