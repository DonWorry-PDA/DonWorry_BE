package com.sol.user.notification.service;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.user.notification.entity.NotificationSetting;
import com.sol.user.notification.repository.NotificationSettingRepository;
import com.sol.user.user.entity.User;
import com.sol.user.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class NotificationSettingCreator {

    private final NotificationSettingRepository notificationSettingRepository;
    private final UserRepository userRepository;

    // REQUIRES_NEW: 별도 트랜잭션으로 커밋하여 중복 시 DataIntegrityViolationException이
    // 외부 트랜잭션(rollback-only 아님)에서 잡힐 수 있도록 분리
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NotificationSetting createDefault(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));
        return notificationSettingRepository.save(NotificationSetting.defaultFor(user));
    }
}
