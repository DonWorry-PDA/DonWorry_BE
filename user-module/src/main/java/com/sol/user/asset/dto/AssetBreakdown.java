package com.sol.user.asset.dto;

import java.math.BigDecimal;

/**
 * 자산 집계 결과(예수금만 계약). 계좌 예수금과 보유종목 평가액을 분해해, 소비처가 필요한 합만 꺼내 쓴다.
 *
 * <ul>
 *   <li>{@code cash} — 전 계좌 deposit_balance(=예수금/현금) 합. 증권 예수금·연금계좌 잔액 포함.
 *   <li>{@code pensionSaving} — IRP·연금저축 잔액(55세 인출제약 트랙).
 *   <li>{@code nonStockHoldingValue} — STOCK 제외 보유종목(ETF·FUND·BOND 등) 평가액 합. 월급 재료.
 *   <li>{@code stockHoldingValue} — 개별주식 평가액 합. 순자산엔 포함하되 월급 재료에선 제외.
 * </ul>
 */
public record AssetBreakdown(
        BigDecimal cash,
        BigDecimal pensionSaving,
        BigDecimal nonStockHoldingValue,
        BigDecimal stockHoldingValue
) {
    /** 월급 재료 총자산 = 예수금 + 비STOCK 보유. 개별주식 제외(청산 전제 자산이라 월급 재원 아님). */
    public BigDecimal operatingTotal() {
        return cash.add(nonStockHoldingValue);
    }

    /** 전체 자산 = 예수금 + 전 보유종목(주식 포함). 순자산·은퇴시뮬 표시용. */
    public BigDecimal grossTotal() {
        return cash.add(nonStockHoldingValue).add(stockHoldingValue);
    }

    /** 즉시 가용 금융자산 = 월급 재료 − 연금저축(55세 제약분). */
    public BigDecimal availableFinancialAsset() {
        return operatingTotal().subtract(pensionSaving).max(BigDecimal.ZERO);
    }
}
