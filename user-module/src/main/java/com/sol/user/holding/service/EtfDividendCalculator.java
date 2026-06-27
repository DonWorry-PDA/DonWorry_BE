package com.sol.user.holding.service;

import com.sol.user.holding.dto.EtfHolding;
import com.sol.user.holding.repository.HoldingRepository;
import com.sol.user.portfolio.infra.rest.ProductBatchClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * 보유 ETF(dividend_history) 기반 월 예상 분배금 합계 — 배당 단일 출처(#216).
 * 시드 DIVIDEND 현금흐름 이벤트를 대체하며, 생활안정도가 사용한다.
 * (월간리포트·현금흐름진단도 동일 공식을 쓰므로 추후 이 헬퍼로 일원화 가능)
 */
@Component
@RequiredArgsConstructor
public class EtfDividendCalculator {

    private final HoldingRepository holdingRepository;
    private final ProductBatchClient productBatchClient;

    /** 사용자 보유 ETF의 월 예상 분배금 합계(원, 반올림). 보유종목이 없으면 0. */
    public BigDecimal monthlyDividend(Long userId) {
        List<EtfHolding> holdings = holdingRepository.findAllHoldingsByUserId(userId);
        if (holdings.isEmpty()) {
            return BigDecimal.ZERO;
        }
        List<Long> productIds = holdings.stream().map(EtfHolding::getProductId).toList();
        Map<Long, BigDecimal> monthlyDividendMap = productBatchClient.fetchEtfMonthlyDividends(productIds);
        return holdings.stream()
                .map(h -> monthlyDividendMap.getOrDefault(h.getProductId(), BigDecimal.ZERO)
                        .multiply(h.getQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(0, RoundingMode.HALF_UP);
    }
}
