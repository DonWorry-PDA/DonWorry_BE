package com.sol.user.portfolio.dto;

import com.sol.user.portfolio.type.AllocationRole;

import java.math.BigDecimal;

/**
 * 화면 배분 막대/도넛의 한 항목. 위험버킷 ETF(holdings)와 안전·단기버킷 집계를 한 리스트로 병합한 표현.
 * ratio는 운용자산(riskTarget+safeTarget+shortTermBucket) 기준 비중(%) — 연금저축은 제외된다.
 */
public record AllocationView(
        String label,
        AllocationRole role,
        BigDecimal ratio,    // 운용자산 대비 비중 (%, 0~100)
        BigDecimal amount    // 금액(원)
) {
}
