package com.sol.user.notification.service;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.user.notification.dto.NotificationSettingResponse;
import com.sol.user.notification.entity.NotificationSetting;
import com.sol.user.notification.repository.NotificationSettingRepository;
import com.sol.user.user.entity.User;
import com.sol.user.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class NotificationSettingService {

    private static final Set<String> VALID_IDS = Set.of("balance", "pension", "dividend", "report");

    private final NotificationSettingRepository notificationSettingRepository;
    private final UserRepository userRepository;

    @Transactional
    public List<NotificationSettingResponse> getSettings(Long userId) {
        NotificationSetting setting = notificationSettingRepository.findById(userId)
                .orElseGet(() -> createDefault(userId));
        return NotificationSettingResponse.from(setting);
    }

    @Transactional
    public void toggleSetting(Long userId, String id, boolean enabled) {
        if (!VALID_IDS.contains(id)) {
            throw new BaseException(ErrorCode.NOTIFICATION_SETTING_NOT_FOUND);
        }
        NotificationSetting setting = notificationSettingRepository.findById(userId)
                .orElseGet(() -> createDefault(userId));
        setting.toggle(id, enabled);
    }

    private NotificationSetting createDefault(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));
        return notificationSettingRepository.save(NotificationSetting.defaultFor(user));
    }
}
