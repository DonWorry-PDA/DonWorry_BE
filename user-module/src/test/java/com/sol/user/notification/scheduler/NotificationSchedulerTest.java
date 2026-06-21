package com.sol.user.notification.scheduler;

import com.sol.user.notification.dto.NotificationTarget;
import com.sol.user.notification.entity.NotificationType;
import com.sol.user.notification.provider.NotificationProvider;
import com.sol.user.notification.repository.NotificationRepository;
import com.sol.user.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationSchedulerTest {

    @Mock NotificationService notificationService;
    @Mock NotificationRepository notificationRepository;

    private NotificationScheduler scheduler;

    private static final NotificationProvider STUB_PROVIDER = new NotificationProvider() {
        @Override
        public NotificationType getType() {
            return NotificationType.DIVIDEND;
        }

        @Override
        public List<NotificationTarget> findTargets() {
            return List.of(
                    new NotificationTarget(1L, "배당금 입금", "삼성전자 배당금이 입금되었습니다.", "/products/001"),
                    new NotificationTarget(2L, "배당금 입금", "삼성전자 배당금이 입금되었습니다.", null)
            );
        }
    };

    @BeforeEach
    void setUp() {
        scheduler = new NotificationScheduler(List.of(STUB_PROVIDER), notificationService, notificationRepository);
    }

    @Test
    @DisplayName("findTargets() 반환 대상에게만 notify()가 호출된다")
    void notifiesOnlyTargets() {
        given(notificationRepository.findSentPairsToday(any())).willReturn(List.of());

        scheduler.run();

        verify(notificationService).notify(eq(1L), eq(NotificationType.DIVIDEND), any(), any(), eq("/products/001"));
        verify(notificationService).notify(eq(2L), eq(NotificationType.DIVIDEND), any(), any(), eq(null));
    }

    @Test
    @DisplayName("오늘 이미 발송된 (userId, type) 쌍은 스킵된다")
    void skipsDuplicateSentToday() {
        Object[] sentRow = new Object[]{1L, NotificationType.DIVIDEND};
        given(notificationRepository.findSentPairsToday(any())).willReturn(List.<Object[]>of(sentRow));

        scheduler.run();

        verify(notificationService, never()).notify(eq(1L), eq(NotificationType.DIVIDEND), any(), any(), any());
        verify(notificationService).notify(eq(2L), eq(NotificationType.DIVIDEND), any(), any(), eq(null));
    }

    @Test
    @DisplayName("linkTarget이 null이어도 notify()가 정상 호출된다")
    void allowsNullLinkTarget() {
        given(notificationRepository.findSentPairsToday(any())).willReturn(List.of());

        scheduler.run();

        verify(notificationService).notify(eq(2L), eq(NotificationType.DIVIDEND), any(), any(), eq(null));
    }
}
