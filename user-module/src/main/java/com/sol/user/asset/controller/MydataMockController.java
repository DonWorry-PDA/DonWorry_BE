package com.sol.user.asset.controller;

import com.sol.common.response.ApiResponse;
import com.sol.user.asset.dto.MydataSyncResponse;
import com.sol.user.asset.service.AssetMockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "마이데이터", description = "마이데이터 연결/동기화 API (목업)")
@RestController
@RequestMapping("/api/user/mydata/mock")
@RequiredArgsConstructor
public class MydataMockController {

    private final AssetMockService assetMockService;

    @Operation(summary = "마이데이터 최초 연결", description = "로그인한 사용자에게 배정된 자산 정보를 불러온다.")
    @PostMapping("/connect")
    public ResponseEntity<ApiResponse<MydataSyncResponse>> connect(
            @RequestAttribute("userId") Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(MydataSyncResponse.from(
                assetMockService.sync(userId),
                "마이데이터 정보를 불러왔습니다."
        )));
    }

    @Operation(summary = "마이데이터 재동기화", description = "이미 연결된 자산 정보를 최신 상태로 다시 불러온다.")
    @PostMapping("/sync")
    public ResponseEntity<ApiResponse<MydataSyncResponse>> sync(
            @RequestAttribute("userId") Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(MydataSyncResponse.from(
                assetMockService.sync(userId),
                "마이데이터 정보를 다시 동기화했습니다."
        )));
    }
}
