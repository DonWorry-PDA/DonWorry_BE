package com.sol.user.portfolio.type;

import java.math.BigDecimal;

/**
 * 안전·단기버킷 sub-bucket 구성의 한 칸: 안전 슬롯 + sub-bucket 내 비중(합=1.0).
 * 위험버킷의 {@link SlotWeight}와 동일 패턴(안전 슬롯용).
 */
public record SafeSlotWeight(SafeSlot slot, BigDecimal weight) {
}
