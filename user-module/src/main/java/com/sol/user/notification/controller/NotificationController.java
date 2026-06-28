package com.sol.user.notification.controller;

import com.sol.common.response.ApiResponse;
import com.sol.user.notification.dto.NotificationResponse;
import com.sol.user.notification.dto.NotificationSettingResponse;
import com.sol.user.notification.dto.NotificationSettingToggleRequest;
import com.sol.user.notification.service.NotificationService;
import com.sol.user.notification.service.NotificationSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@Tag(name = "Notification", description = "알림 API")
@RestController
@RequestMapping("/api/user/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationSettingService notificationSettingService;

    @Operation(summary = "SSE 알림 구독", description = "실시간 알림 수신을 위한 SSE 연결")
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@RequestAttribute("userId") Long userId) {
        return notificationService.subscribe(userId);
    }

    @Operation(summary = "내 알림 목록 조회", description = "최신순으로 반환")
    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getNotifications(
            @RequestAttribute("userId") Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(notificationService.getNotifications(userId)));
    }

    @Operation(summary = "읽지 않은 알림 수 조회", description = "홈 화면 알림 배지용")
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Long>> getUnreadCount(
            @RequestAttribute("userId") Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(notificationService.getUnreadCount(userId)));
    }

    @Operation(summary = "전체 알림 읽음 처리")
    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead(@RequestAttribute("userId") Long userId) {
        notificationService.markAllAsRead(userId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @Operation(summary = "알림 읽음 처리")
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @PathVariable Long notificationId,
            @RequestAttribute("userId") Long userId) {
        notificationService.markAsRead(notificationId, userId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @Operation(summary = "알림 설정 조회")
    @GetMapping("/settings")
    public ResponseEntity<ApiResponse<List<NotificationSettingResponse>>> getSettings(
            @RequestAttribute("userId") Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(notificationSettingService.getSettings(userId)));
    }

    @Operation(summary = "알림 설정 토글")
    @PatchMapping("/settings/{id}")
    public ResponseEntity<ApiResponse<Void>> toggleSetting(
            @PathVariable String id,
            @Valid @RequestBody NotificationSettingToggleRequest request,
            @RequestAttribute("userId") Long userId) {
        notificationSettingService.toggleSetting(userId, id, request.enabled());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
