package com.sol.user.holding.service;

import com.sol.user.holding.dto.HoldingDividendCalendarProjection;
import com.sol.user.holding.dto.HoldingDividendPaymentProjection;
import com.sol.user.holding.dto.HoldingWithProduct;
import com.sol.user.holding.repository.HoldingRepository;
import com.sol.user.portfolio.infra.rest.ProductBatchClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 보유 ETF(dividend_history) 기반 월 분배금의 <b>단일 출처</b>(#303).
 * 종목별 "월 분배금 단가(amount_per_unit ÷ interval) × 수량"을 합산하며,
 * 연금/비연금 분해와 월급 제외종목 차감을 한곳에서 처리한다 — 화면마다 다른 분배금을 없애기 위함.
 *
 * <p>세전(gross)만 산출한다. 세후(net, -15.4%)·이자·국민연금 결합은 화면별 표시 정책이라
 * 소비처가 명시적으로 가공한다(자산분석=세전, 현금흐름 진단=세후). 단가는 모두 여기서 나온다.
 *
 * <p>예전엔 자산허브·자산분석·현금흐름·생활안정도가 각자 분배금을 다시 계산해
 * 같은 사용자도 화면마다 금액이 달랐다(세전/세후·연금포함·제외 조합이 제각각). 이제 단가·합산은 이 헬퍼가
 * 단일 진실원천이고, 소비처는 (연금 분해/제외/세금)만 선택한다.
 */
@Component
@RequiredArgsConstructor
public class EtfDividendCalculator {

    /** 55세 인출제약이 걸린 연금 계좌 — 분배금이 나와도 즉시가용이 아니라 별도(locked) 집계. */
    private static final Set<String> PENSION_ACCOUNT_TYPES = Set.of("IRP", "PENSION_SAVING");

    private final HoldingRepository holdingRepository;
    private final ProductBatchClient productBatchClient;

    /**
     * 전체(연금+비연금) 세전 월 분배금 합계(원, 반올림). 제외 없음. 보유종목이 없으면 0.
     * 생활안정도 재계산은 자산 동기화 트랜잭션 경로에서도 호출되므로, Product 서비스 조회
     * 실패는 격리(fail-open)해 0으로 처리한다 — 외부 장애가 sync·재계산을 롤백시키지 않게 한다.
     */
    public BigDecimal monthlyDividend(Long userId) {
        return monthlyDividendBreakdown(userId, Set.of()).totalGrossRounded();
    }

    /**
     * 보유 ETF의 월 분배금을 연금/비연금으로 분해한 세전 합계(반올림 전 원시값).
     * {@code excludedHoldingIds}는 월급 만들기에서 제외한 보유종목(HOLDING_*)을 분배금에서도 뺀다.
     * 반올림은 소비처가 표시 단계에서 수행하도록 원시 정밀도를 유지한다.
     */
    public DividendBreakdown monthlyDividendBreakdown(Long userId, Set<Long> excludedHoldingIds) {
        List<HoldingWithProduct> holdings = holdingRepository.findHoldingsWithAccountTypeByUserId(userId).stream()
                .filter(holding -> !excludedHoldingIds.contains(holding.getHoldingId()))
                .toList();
        if (holdings.isEmpty()) {
            return DividendBreakdown.ZERO;
        }

        List<Long> productIds = holdings.stream().map(HoldingWithProduct::getProductId).toList();
        Map<Long, BigDecimal> monthlyDividendMap;
        try {
            monthlyDividendMap = productBatchClient.fetchEtfMonthlyDividends(productIds);
        } catch (RuntimeException e) {
            return DividendBreakdown.ZERO;
        }

        BigDecimal nonPension = BigDecimal.ZERO;
        BigDecimal pension = BigDecimal.ZERO;
        for (HoldingWithProduct holding : holdings) {
            BigDecimal dividend = monthlyDividendMap.getOrDefault(holding.getProductId(), BigDecimal.ZERO)
                    .multiply(nz(holding.getQuantity()));
            if (PENSION_ACCOUNT_TYPES.contains(holding.getAccountType())) {
                pension = pension.add(dividend);
            } else {
                nonPension = nonPension.add(dividend);
            }
        }
        return new DividendBreakdown(nonPension, pension);
    }

    /** 비연금(즉시가용)·연금(locked) 세전 월 분배금 분해. */
    public record DividendBreakdown(BigDecimal nonPensionGross, BigDecimal pensionGross) {
        public static final DividendBreakdown ZERO = new DividendBreakdown(BigDecimal.ZERO, BigDecimal.ZERO);

        /** 반올림 전 원시 합계 — 세후 변환처럼 추가 연산이 따르는 소비처(현금흐름 진단)용. */
        public BigDecimal totalGross() {
            return nonPensionGross.add(pensionGross);
        }

        /**
         * 화면 표시용 합계 — 스코프별로 반올림한 뒤 더한다. 자산분석은 비연금/연금을 각각 정수 원으로
         * 표시(round(a)+round(b))하므로, 허브·생활안정도의 총 분배금도 같은 기준으로 맞춰 화면 간 1원
         * 오차(round(a+b) ≠ round(a)+round(b))를 없앤다.
         */
        public BigDecimal totalGrossRounded() {
            return nonPensionGross.setScale(0, RoundingMode.HALF_UP)
                    .add(pensionGross.setScale(0, RoundingMode.HALF_UP));
        }
    }

    /**
     * 특정 달(ym)에 실제로 분배가 발생하는 ETF만 합산한 세전 배당(원, 반올림).
     * 캘린더 화면과 동일한 알고리즘을 사용한다:
     * <ol>
     *   <li>당월 실지급 내역을 합산하고, 해당 종목·월을 actualMonths 셋에 기록한다.</li>
     *   <li>보유 ETF의 분배 그리드(latestPaymentDate + interval)를 걸어 당월에 착지하는 종목만
     *       합산한다(단, actualMonths에 이미 있으면 중복 제외).</li>
     * </ol>
     */
    public BigDecimal actualMonthlyDividend(Long userId, YearMonth ym) {
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();

        // Step 1 — 실제 지급 확정 내역
        List<HoldingDividendPaymentProjection> actuals =
                holdingRepository.findDividendPaymentsByUserId(userId, from, to);
        Set<String> actualMonths = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        for (HoldingDividendPaymentProjection p : actuals) {
            if (p.getProductId() == null || p.getQuantity() == null
                    || p.getAmountPerUnit() == null || p.getPaymentDate() == null) {
                continue;
            }
            total = total.add(
                    p.getAmountPerUnit().multiply(p.getQuantity()).setScale(0, RoundingMode.HALF_UP));
            actualMonths.add(p.getProductId() + "-" + YearMonth.from(p.getPaymentDate()));
        }

        // Step 2 — 그리드 기반 투영(실지급이 없는 종목만)
        List<HoldingDividendCalendarProjection> inputs =
                holdingRepository.findDividendCalendarInputsByUserId(userId);
        for (HoldingDividendCalendarProjection input : inputs) {
            if (!canProject(input)) continue;
            long interval = input.getDistributionIntervalMonths();
            LocalDate projected = input.getLatestPaymentDate();
            // 그리드를 from 이전까지 역방향으로 이동
            while (projected.isAfter(from)) {
                projected = projected.minusMonths(interval);
            }
            // 그리드를 from 이상인 첫 날짜로 순방향 이동
            while (projected.isBefore(from)) {
                projected = projected.plusMonths(interval);
            }
            // projected 는 이제 from 이상인 첫 그리드 날짜
            while (!projected.isAfter(to)) {
                String key = input.getProductId() + "-" + YearMonth.from(projected);
                if (!actualMonths.contains(key)) {
                    total = total.add(
                            input.getAmountPerUnit().multiply(input.getQuantity())
                                    .setScale(0, RoundingMode.HALF_UP));
                }
                projected = projected.plusMonths(interval);
            }
        }
        return total;
    }

    private boolean canProject(HoldingDividendCalendarProjection input) {
        return input.getProductId() != null
                && input.getQuantity() != null
                && input.getAmountPerUnit() != null
                && input.getLatestPaymentDate() != null
                && input.getDistributionIntervalMonths() != null
                && input.getDistributionIntervalMonths() > 0;
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
