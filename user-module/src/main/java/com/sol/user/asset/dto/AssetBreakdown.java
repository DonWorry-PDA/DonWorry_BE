package com.sol.user.asset.dto;

import java.math.BigDecimal;

/**
 * 자산 집계 결과(예수금만 계약). 계좌 예수금과 보유종목 평가액을 계좌·종목 성격별로 분해해,
 * 소비처가 필요한 합만 꺼내 쓴다.
 *
 * <ul>
 *   <li>{@code cash} — 전 계좌 deposit_balance(=예수금/현금) 합. 증권 예수금·연금계좌 잔액 포함.
 *   <li>{@code pensionCash} — IRP·연금저축 계좌 예수금(55세 인출제약 트랙).
 *   <li>{@code nonStockHoldingValue} — 비연금계좌의 STOCK 제외 보유종목(ETF·FUND·BOND 등) 평가액. 즉시가용 월급 재료.
 *   <li>{@code pensionHoldingValue} — 연금계좌(IRP·연금저축)의 STOCK 제외 보유종목 평가액. 월급 재료지만 55세 제약.
 *   <li>{@code stockHoldingValue} — 개별주식 평가액 합(계좌 무관). 순자산엔 포함하되 월급 재료에선 제외.
 * </ul>
 *
 * <p>연금계좌 보유종목을 {@code pensionHoldingValue}로 분리한 이유: 분리 전엔 연금계좌 ETF가
 * {@code nonStockHoldingValue}에 섞여 {@link #availableFinancialAsset()}가 연금 예수금만 차감해
 * 55세 제약 자산을 즉시가용으로 과대계상했다. 현재 mock은 연금계좌 보유종목을 시드하지 않아 0이지만,
 * 실 마이데이터·연금 ETF 운용 연동 시 즉시 발현될 계약을 미리 고정한다.
 */
public record AssetBreakdown(
        BigDecimal cash,
        BigDecimal pensionCash,
        BigDecimal nonStockHoldingValue,
        BigDecimal pensionHoldingValue,
        BigDecimal stockHoldingValue
) {
    /** 월급 재료 총자산 = 예수금 + 전 비STOCK 보유(연금 종목 포함). 개별주식 제외(청산 전제라 월급 재원 아님). */
    public BigDecimal operatingTotal() {
        return cash.add(nonStockHoldingValue).add(pensionHoldingValue);
    }

    /** 전체 자산 = 월급 재료 + 개별주식. 순자산·은퇴시뮬 표시용. */
    public BigDecimal grossTotal() {
        return operatingTotal().add(stockHoldingValue);
    }

    /** 55세 인출제약 연금자산 = 연금 예수금 + 연금 보유종목. */
    public BigDecimal restrictedPension() {
        return pensionCash.add(pensionHoldingValue);
    }

    /** 즉시 가용 금융자산 = 월급 재료 − 연금 제약분(예수금+종목). */
    public BigDecimal availableFinancialAsset() {
        return operatingTotal().subtract(restrictedPension()).max(BigDecimal.ZERO);
    }
}
