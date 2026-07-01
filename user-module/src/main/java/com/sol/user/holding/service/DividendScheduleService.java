package com.sol.user.holding.service;

import com.sol.user.holding.dto.HoldingDividendCalendarProjection;
import com.sol.user.holding.dto.HoldingDividendPaymentProjection;
import com.sol.user.holding.repository.HoldingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 분배금 <b>일정(스케줄)</b>의 단일 출처 — dividend_history 실지급 + interval 격자 투영(#307).
 * 캘린더가 날짜별로 찍던 로직을 추출해, 캘린더와 "이번 달 수입"(홈·리포트)이 <b>같은 분배금</b>을 쓰게 한다.
 *
 * <p>{@link EtfDividendCalculator}의 런레이트(월 평균 예상)와는 렌즈가 다르다: 이쪽은 "그 달에 실제로
 * 지급/예정된 분배금"이라, 분기배당이면 지급월엔 크고 비지급월엔 0이다. 캘린더에 찍히는 값과 정확히 일치한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DividendScheduleService {

    private final HoldingRepository holdingRepository;

    /** 조회월에 지급/예정된 분배금 세전 합계. 캘린더가 그 달에 표시하는 분배금 합과 정의상 일치한다. */
    public BigDecimal monthlyGross(Long userId, YearMonth ym) {
        return occurrences(userId, ym.atDay(1), ym.atEndOfMonth()).stream()
                .map(DividendOccurrence::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * [from, to] 구간의 분배금 발생 목록. 실지급(actual)이 있는 (productId, 월)은 확정으로 넣고,
     * 같은 (productId, 월)의 투영(expected)은 건너뛴다 — 캘린더의 중복 방지 규칙과 동일.
     */
    public List<DividendOccurrence> occurrences(Long userId, LocalDate from, LocalDate to) {
        List<DividendOccurrence> occurrences = new ArrayList<>();
        Set<String> actualMonths = new HashSet<>();
        addActual(occurrences, actualMonths, userId, from, to);
        addExpected(occurrences, actualMonths, userId, from, to);
        return occurrences;
    }

    private void addActual(
            List<DividendOccurrence> occurrences, Set<String> actualMonths,
            Long userId, LocalDate from, LocalDate to) {
        for (HoldingDividendPaymentProjection input
                : holdingRepository.findDividendPaymentsByUserId(userId, from, to)) {
            if (input.getProductId() == null || input.getQuantity() == null
                    || input.getAmountPerUnit() == null || input.getPaymentDate() == null) {
                continue;
            }
            BigDecimal amount = input.getAmountPerUnit()
                    .multiply(input.getQuantity())
                    .setScale(0, RoundingMode.HALF_UP);
            occurrences.add(new DividendOccurrence(
                    input.getProductId(), input.getPaymentDate(), amount,
                    input.getProductName(), false));
            actualMonths.add(monthKey(input.getProductId(), input.getPaymentDate()));
        }
    }

    private void addExpected(
            List<DividendOccurrence> occurrences, Set<String> actualMonths,
            Long userId, LocalDate from, LocalDate to) {
        for (HoldingDividendCalendarProjection input
                : holdingRepository.findDividendCalendarInputsByUserId(userId)) {
            if (!canProject(input)) {
                continue;
            }
            long interval = input.getDistributionIntervalMonths();
            BigDecimal amount = input.getAmountPerUnit()
                    .multiply(input.getQuantity())
                    .setScale(0, RoundingMode.HALF_UP);

            // 앵커(최신지급일)에서 interval 격자로 [from, to]를 덮는 첫 지점으로 이동(#307).
            LocalDate projectedDate = input.getLatestPaymentDate();
            while (projectedDate.isAfter(from)) {
                projectedDate = projectedDate.minusMonths(interval);
            }
            while (projectedDate.isBefore(from)) {
                projectedDate = projectedDate.plusMonths(interval);
            }
            while (!projectedDate.isAfter(to)) {
                if (!actualMonths.contains(monthKey(input.getProductId(), projectedDate))) {
                    occurrences.add(new DividendOccurrence(
                            input.getProductId(), projectedDate, amount,
                            input.getProductName(), true));
                }
                projectedDate = projectedDate.plusMonths(interval);
            }
        }
    }

    private boolean canProject(HoldingDividendCalendarProjection input) {
        return input.getProductId() != null
                && input.getQuantity() != null
                && input.getAmountPerUnit() != null
                && input.getLatestPaymentDate() != null
                && input.getDistributionIntervalMonths() != null
                && input.getDistributionIntervalMonths() > 0;
    }

    /** 분배금 중복 방지 키 — (productId, 해당 월). */
    private String monthKey(Long productId, LocalDate date) {
        return productId + "-" + YearMonth.from(date);
    }

    /**
     * 분배금 1건 — 캘린더 렌더링(제목·id·estimated 플래그)과 월 합계 모두 이 목록에서 나온다.
     * {@code amount}는 세전(gross), 원 단위 반올림.
     */
    public record DividendOccurrence(
            Long productId,
            LocalDate date,
            BigDecimal amount,
            String productName,
            boolean estimated
    ) {
    }
}
