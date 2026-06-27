package com.sol.user.asset.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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

    /** 잠자는 돈 = 비연금 예수금(전체 예수금 − 연금 예수금). 음수 방지. */
    public BigDecimal idleCash() {
        return cash.subtract(pensionCash).max(BigDecimal.ZERO);
    }

    /**
     * 자산을 4역할(현금흐름·성장·잠자는 돈·연금)로 분해한 결과. 투자 건강검진 헤드라인·도넛, 허브 미리보기가
     * 모두 이 한 메서드를 단일 출처로 쓴다. 금액 0인 역할은 빼고, 남은 역할의 {@code ratio} 합이 정확히 100이
     * 되도록 largest-remainder(내림 후 소수부 큰 순서로 1씩 보정)로 맞춘다. 순자산 0이면 빈 리스트.
     *
     * <p>헤드라인 비율을 이 분해의 {@code CASHFLOW} ratio에서 가져오면(↔ {@link #cashflowAssetRatio()})
     * 헤드라인 숫자와 도넛 조각이 항상 일치한다(독립 반올림 시 1%p 어긋남 방지).
     */
    public List<RoleSlice> roleAllocation() {
        BigDecimal total = grossTotal();
        if (total.signum() <= 0) {
            return List.of();
        }

        List<Slice> slices = new ArrayList<>();
        addSlice(slices, AssetRole.CASHFLOW, nonStockHoldingValue, total);
        addSlice(slices, AssetRole.GROWTH, stockHoldingValue, total);
        addSlice(slices, AssetRole.IDLE, idleCash(), total);
        addSlice(slices, AssetRole.PENSION, restrictedPension(), total);

        int leftover = 100 - slices.stream().mapToInt(s -> s.ratio).sum();
        slices.stream()
                .sorted(Comparator.comparing((Slice s) -> s.remainder).reversed())
                .limit(Math.max(leftover, 0))
                .forEach(s -> s.ratio++);

        return slices.stream()
                .map(s -> new RoleSlice(s.role, s.amount, s.ratio))
                .toList();
    }

    /**
     * 현금흐름(월급 만드는) 자산 비율(정수 %). {@link #roleAllocation()}의 CASHFLOW ratio와 동일하다.
     * 순자산이 0이면 {@code null}, 자산은 있으나 현금흐름 자산이 없으면 0.
     */
    public Integer cashflowAssetRatio() {
        List<RoleSlice> slices = roleAllocation();
        if (slices.isEmpty()) {
            return null;
        }
        return slices.stream()
                .filter(s -> s.role() == AssetRole.CASHFLOW)
                .map(RoleSlice::ratio)
                .findFirst()
                .orElse(0);
    }

    private static void addSlice(List<Slice> slices, AssetRole role, BigDecimal amount, BigDecimal total) {
        if (amount.signum() <= 0) {
            return;
        }
        BigDecimal pct = amount.multiply(BigDecimal.valueOf(100))
                .divide(total, 4, RoundingMode.HALF_UP);
        int floor = pct.setScale(0, RoundingMode.DOWN).intValue();
        slices.add(new Slice(role, amount, floor, pct.subtract(BigDecimal.valueOf(floor))));
    }

    /** 자산 역할. 표시·집계 순서는 선언 순서 고정(현금흐름·성장·잠자는 돈·연금). */
    public enum AssetRole {
        CASHFLOW("현금흐름"),
        GROWTH("성장"),
        IDLE("잠자는 돈"),
        PENSION("연금");

        private final String label;

        AssetRole(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    /** 역할별 금액·비율(순자산 대비 %, 합 100). 금액 0 역할은 분해에서 제외돼 등장하지 않는다. */
    public record RoleSlice(AssetRole role, BigDecimal amount, int ratio) {
    }

    /** ratio 보정용 가변 초안. */
    private static final class Slice {
        private final AssetRole role;
        private final BigDecimal amount;
        private int ratio;
        private final BigDecimal remainder;

        private Slice(AssetRole role, BigDecimal amount, int ratio, BigDecimal remainder) {
            this.role = role;
            this.amount = amount;
            this.ratio = ratio;
            this.remainder = remainder;
        }
    }
}
