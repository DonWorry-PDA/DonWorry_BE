package com.sol.user.notification.service;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.user.notification.dto.NotificationSettingResponse;
import com.sol.user.notification.entity.NotificationSetting;
import com.sol.user.notification.repository.NotificationSettingRepository;
import com.sol.user.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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
    private final NotificationSettingCreator notificationSettingCreator;

    @Transactional
    public List<NotificationSettingResponse> getSettings(Long userId) {
        return NotificationSettingResponse.from(getOrCreateDefault(userId));
    }

    @Transactional
    public void toggleSetting(Long userId, String id, boolean enabled) {
        if (!VALID_IDS.contains(id)) {
            throw new BaseException(ErrorCode.INVALID_INPUT);
        }
        getOrCreateDefault(userId).toggle(id, enabled);
    }

    private NotificationSetting getOrCreateDefault(Long userId) {
        return notificationSettingRepository.findById(userId).orElseGet(() -> {
            try {
                return notificationSettingCreator.createDefault(userId);
            } catch (DataIntegrityViolationException e) {
                // 동시 요청이 먼저 INSERT를 커밋한 경우, 해당 레코드를 읽어 반환
                return notificationSettingRepository.findById(userId)
                        .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));
            }
        });
    }
}
