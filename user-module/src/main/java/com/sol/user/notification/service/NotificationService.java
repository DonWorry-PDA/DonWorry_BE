package com.sol.user.notification.service;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.user.notification.dto.NotificationResponse;
import com.sol.user.notification.entity.Notification;
import com.sol.user.notification.entity.NotificationType;
import com.sol.user.notification.repository.NotificationRepository;
import com.sol.user.notification.repository.SseEmitterRepository;
import com.sol.user.user.entity.User;
import com.sol.user.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final long SSE_TIMEOUT = 60 * 60 * 1000L;

    private final NotificationRepository notificationRepository;
    private final SseEmitterRepository sseEmitterRepository;
    private final UserRepository userRepository;

    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        sseEmitterRepository.save(userId, emitter);

        emitter.onCompletion(() -> sseEmitterRepository.delete(userId, emitter));
        emitter.onTimeout(() -> sseEmitterRepository.delete(userId, emitter));
        emitter.onError(e -> {
            log.warn("SSE 연결 오류 userId={}: {}", userId, e.getMessage());
            sseEmitterRepository.delete(userId, emitter);
        });

        try {
            emitter.send(SseEmitter.event().name("connect").data("connected"));
        } catch (IOException e) {
            log.error("SSE 초기 이벤트 전송 실패 userId={}: {}", userId, e.getMessage(), e);
            sseEmitterRepository.delete(userId, emitter);
        }

        return emitter;
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotifications(Long userId) {
        return notificationRepository.findTop10ByUserUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional
    public void notify(Long userId, NotificationType type, String title, String content) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

        Notification notification = Notification.builder()
                .user(user)
                .notificationType(type)
                .title(title)
                .content(content)
                .build();

        notificationRepository.save(notification);

        NotificationResponse response = NotificationResponse.from(notification);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sendToEmitter(userId, response);
            }
        });
    }

    private void sendToEmitter(Long userId, NotificationResponse data) {
        Set<SseEmitter> userEmitters = Set.copyOf(sseEmitterRepository.findByUserId(userId));
        for (SseEmitter emitter : userEmitters) {
            try {
                emitter.send(SseEmitter.event().name("notification").data(data));
            } catch (IOException e) {
                log.warn("SSE 전송 실패 userId={}: {}", userId, e.getMessage());
                sseEmitterRepository.delete(userId, emitter);
            }
        }
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepository.markAllAsReadByUserId(userId);
    }

    @Transactional
    public void markAsRead(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOTIFICATION_NOT_FOUND));

        if (!notification.getUser().getUserId().equals(userId)) {
            throw new BaseException(ErrorCode.NOTIFICATION_NOT_FOUND);
        }

        notification.markAsRead();
    }
}
