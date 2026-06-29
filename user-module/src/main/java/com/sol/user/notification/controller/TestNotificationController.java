package com.sol.user.notification.controller;

import com.sol.common.response.ApiResponse;
import com.sol.user.notification.dto.TestNotifyRequest;
import com.sol.user.notification.scheduler.NotificationScheduler;
import com.sol.user.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Test", description = "개발용 테스트 API")
@RestController
@RequestMapping("/api/user/test")
@RequiredArgsConstructor
public class TestNotificationController {

    private final NotificationService notificationService;
    private final NotificationScheduler notificationScheduler;

    @Operation(summary = "알림 수동 발송 (개발용)", description = "targetUserId에게 알림 DB 저장 + SSE 전송")
    @PostMapping("/notify")
    public ResponseEntity<ApiResponse<Void>> sendTestNotification(@Valid @RequestBody TestNotifyRequest request) {
        notificationService.notify(
                request.targetUserId(),
                request.type(),
                request.title(),
                request.content(),
                request.linkTarget()
        );
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @Operation(summary = "알림 스케줄러 수동 실행 (개발용)", description = "Provider 전체를 즉시 실행해 오늘 대상자에게 알림을 발송한다")
    @PostMapping("/notify/run-scheduler")
    public ResponseEntity<ApiResponse<Void>> runScheduler() {
        notificationScheduler.run();
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
