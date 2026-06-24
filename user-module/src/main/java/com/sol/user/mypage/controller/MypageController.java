package com.sol.user.mypage.controller;

import com.sol.common.response.ApiResponse;
import com.sol.user.mypage.dto.MypageResponse;
import com.sol.user.mypage.dto.MypageUpdateRequest;
import com.sol.user.mypage.service.MypageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "마이페이지", description = "마이페이지 수정 API")
@RestController
@RequestMapping("/api/user/mypage")
@RequiredArgsConstructor
public class MypageController {

    private final MypageService mypageService;

    @Operation(summary = "마이페이지 정보 수정",
            description = "나이·은퇴여부·연금수령여부·목표 생활비를 수정한다. 미입력 항목은 기존 값을 유지한다.")
    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<MypageResponse>> updateMypage(
            @RequestAttribute("userId") Long userId,
            @RequestBody MypageUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(mypageService.update(userId, request)));
    }
}
