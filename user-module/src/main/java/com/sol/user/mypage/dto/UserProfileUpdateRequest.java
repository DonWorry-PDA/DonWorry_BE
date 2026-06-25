package com.sol.user.mypage.dto;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;

import java.math.BigDecimal;

public record UserProfileUpdateRequest(
        Integer age,
        String status,
        String pensionStatus,
        BigDecimal monthlyTargetKrw
) {
    public Boolean retiredValue() {
        if (status == null) return null;
        if ("은퇴 후".equals(status)) return true;
        if ("은퇴 전".equals(status)) return false;
        throw new BaseException(ErrorCode.INVALID_INPUT);
    }

    public Boolean nationalPensionReceivingValue() {
        if (pensionStatus == null) return null;
        if ("수령 중".equals(pensionStatus)) return true;
        if ("수령 전".equals(pensionStatus)) return false;
        throw new BaseException(ErrorCode.INVALID_INPUT);
    }
}
