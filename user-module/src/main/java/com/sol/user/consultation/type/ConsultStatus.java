package com.sol.user.consultation.type;

/** 상담 상태. FE는 reserved/completed만 노출하며, CANCELLED는 취소된 예약. */
public enum ConsultStatus {
    RESERVED,
    COMPLETED,
    CANCELLED
}
