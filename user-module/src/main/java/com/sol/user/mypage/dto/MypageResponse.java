package com.sol.user.mypage.dto;

import java.math.BigDecimal;

public record MypageResponse(
        Long userId,
        Integer age,
        Boolean retired,
        Boolean nationalPensionReceiving,
        BigDecimal monthlyTargetLivingCost
) {
}
