package com.sol.user.portfolio.type;

import java.math.BigDecimal;

/**
 * 안 구성의 한 칸: 코어 슬롯 + 위험버킷 내 비중(합=1.0).
 */
public record SlotWeight(CoreSlot slot, BigDecimal weight) {
}
