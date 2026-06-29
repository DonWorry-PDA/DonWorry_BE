package com.sol.user.notification.service;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.user.notification.dto.MarketOpenReminderResponse;
import com.sol.user.notification.dto.NotificationSettingResponse;
import com.sol.user.notification.entity.NotificationSetting;
import com.sol.user.notification.mapper.NotificationSettingMapper;
import com.sol.user.notification.repository.NotificationSettingRepository;
import com.sol.user.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class NotificationSettingServiceTest {

    @Mock NotificationSettingRepository notificationSettingRepository;
    @Mock NotificationSettingCreator notificationSettingCreator;
    @Mock NotificationSettingMapper notificationSettingMapper;

    @InjectMocks NotificationSettingService notificationSettingService;

    @Test
    @DisplayName("getSettings - 기존 설정이 있으면 mapper를 통해 응답을 반환한다")
    void getSettings_returnsResponseViaMappedSetting() {
        Long userId = 1L;
        NotificationSetting setting = defaultSetting();
        List<NotificationSettingResponse> expected = List.of(
                new NotificationSettingResponse("balance", true),
                new NotificationSettingResponse("pension", true),
                new NotificationSettingResponse("dividend", true),
                new NotificationSettingResponse("report", true)
        );
        given(notificationSettingRepository.findById(userId)).willReturn(Optional.of(setting));
        given(notificationSettingMapper.toSettingResponses(setting)).willReturn(expected);

        List<NotificationSettingResponse> result = notificationSettingService.getSettings(userId);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("toggleSetting - 유효하지 않은 id면 INVALID_INPUT 예외가 발생한다")
    void toggleSetting_throwsOnInvalidId() {
        Long userId = 1L;

        assertThatThrownBy(() -> notificationSettingService.toggleSetting(userId, "unknown", true))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining(ErrorCode.INVALID_INPUT.getMessage());
    }

    @Test
    @DisplayName("subscribeMarketOpenReminder - 플래그가 true로 저장되고 mapper를 통해 응답을 반환한다")
    void subscribeMarketOpenReminder_setsFlagAndReturnsResponse() {
        Long userId = 1L;
        NotificationSetting setting = defaultSetting();
        MarketOpenReminderResponse expected = new MarketOpenReminderResponse(true);
        given(notificationSettingRepository.findById(userId)).willReturn(Optional.of(setting));
        given(notificationSettingMapper.toMarketOpenReminderResponse(setting)).willReturn(expected);

        MarketOpenReminderResponse result = notificationSettingService.subscribeMarketOpenReminder(userId);

        assertThat(setting.isMarketOpenReminder()).isTrue();
        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("subscribeMarketOpenReminder - 이미 구독 중이어도 플래그가 유지되고 정상 응답한다")
    void subscribeMarketOpenReminder_idempotentWhenAlreadySubscribed() {
        Long userId = 1L;
        NotificationSetting setting = defaultSetting();
        setting.subscribeMarketOpenReminder();
        MarketOpenReminderResponse expected = new MarketOpenReminderResponse(true);
        given(notificationSettingRepository.findById(userId)).willReturn(Optional.of(setting));
        given(notificationSettingMapper.toMarketOpenReminderResponse(setting)).willReturn(expected);

        MarketOpenReminderResponse result = notificationSettingService.subscribeMarketOpenReminder(userId);

        assertThat(setting.isMarketOpenReminder()).isTrue();
        assertThat(result).isEqualTo(expected);
    }

    private NotificationSetting defaultSetting() {
        return NotificationSetting.defaultFor(mock(User.class));
    }
}
