package com.sol.user.holding.service;

import com.sol.user.holding.dto.HoldingWithProduct;
import com.sol.user.holding.repository.HoldingRepository;
import com.sol.user.portfolio.infra.rest.ProductBatchClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
        return monthlyDividendBreakdown(userId, Set.of())
                .totalGross()
                .setScale(0, RoundingMode.HALF_UP);
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

        public BigDecimal totalGross() {
            return nonPensionGross.add(pensionGross);
        }
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
