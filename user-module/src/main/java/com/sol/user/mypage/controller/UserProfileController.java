package com.sol.user.mypage.controller;

import com.sol.common.response.ApiResponse;
import com.sol.user.mypage.dto.UserProfileResponse;
import com.sol.user.mypage.dto.UserProfileUpdateRequest;
import com.sol.user.mypage.service.MypageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "프로필", description = "사용자 프로필 조회·수정 API")
@RestController
@RequestMapping("/api/user/profile")
@RequiredArgsConstructor
public class UserProfileController {

    private final MypageService mypageService;

    @Operation(summary = "프로필 조회", description = "이름·나이·은퇴 여부·연금 수령 여부·목표 생활비를 반환한다.")
    @GetMapping
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile(
            @RequestAttribute("userId") Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(mypageService.getProfile(userId)));
    }

    @Operation(summary = "프로필 수정", description = "나이·은퇴 여부·연금 수령 여부·목표 생활비를 수정한다. 미입력 항목은 기존 값을 유지한다.")
    @PatchMapping
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(
            @RequestAttribute("userId") Long userId,
            @RequestBody UserProfileUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(mypageService.updateProfile(userId, request)));
    }
}
