package com.sol.user.notification.service;

import com.sol.user.notification.repository.NotificationRepository;
import com.sol.user.notification.repository.SseEmitterRepository;
import com.sol.user.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock SseEmitterRepository sseEmitterRepository;
    @Mock NotificationRepository notificationRepository;
    @Mock UserRepository userRepository;

    @InjectMocks NotificationService notificationService;

    @Test
    @DisplayName("subscribe 호출 시 SseEmitter가 반환되고 repository에 저장된다")
    void subscribe_savesAndReturnsEmitter() {
        Long userId = 1L;
        given(sseEmitterRepository.save(eq(userId), any(SseEmitter.class)))
                .willAnswer(inv -> inv.getArgument(1));

        SseEmitter result = notificationService.subscribe(userId);

        assertThat(result).isNotNull();
        verify(sseEmitterRepository).save(eq(userId), any(SseEmitter.class));
    }

    @Test
    @DisplayName("sendHeartbeat 호출 시 연결된 모든 emitter에 ping이 전송된다")
    void sendHeartbeat_sendsToAllEmitters() throws IOException {
        Long userId = 1L;
        SseEmitter emitter = mock(SseEmitter.class);
        ConcurrentHashMap<Long, Set<SseEmitter>> map = new ConcurrentHashMap<>();
        map.put(userId, ConcurrentHashMap.newKeySet());
        map.get(userId).add(emitter);
        given(sseEmitterRepository.findAll()).willReturn(map);

        notificationService.sendHeartbeat();

        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName("sendHeartbeat 중 IOException 발생 시 해당 emitter가 repository에서 제거된다")
    void sendHeartbeat_removesEmitterOnIoException() throws IOException {
        Long userId = 1L;
        SseEmitter emitter = mock(SseEmitter.class);
        ConcurrentHashMap<Long, Set<SseEmitter>> map = new ConcurrentHashMap<>();
        map.put(userId, ConcurrentHashMap.newKeySet());
        map.get(userId).add(emitter);
        given(sseEmitterRepository.findAll()).willReturn(map);
        willThrow(new IOException("연결 끊김")).given(emitter).send(any(SseEmitter.SseEventBuilder.class));

        notificationService.sendHeartbeat();

        verify(sseEmitterRepository).delete(userId, emitter);
    }

    @Test
    @DisplayName("sendHeartbeat 정상 전송 시 emitter가 제거되지 않는다")
    void sendHeartbeat_doesNotRemoveEmitterOnSuccess() throws IOException {
        Long userId = 1L;
        SseEmitter emitter = mock(SseEmitter.class);
        ConcurrentHashMap<Long, Set<SseEmitter>> map = new ConcurrentHashMap<>();
        map.put(userId, ConcurrentHashMap.newKeySet());
        map.get(userId).add(emitter);
        given(sseEmitterRepository.findAll()).willReturn(map);

        notificationService.sendHeartbeat();

        verify(sseEmitterRepository, never()).delete(any(), any());
    }
}
