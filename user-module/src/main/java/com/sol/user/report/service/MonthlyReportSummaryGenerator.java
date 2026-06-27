package com.sol.user.report.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 월간 리포트 세 줄 요약 문장 생성기.
 * 규칙 기반(if-else)으로 항상 정확히 3개 문장을 반환한다.
 *
 * <ol>
 *   <li>배당/이자 수입 변화</li>
 *   <li>다음 달 잔액 충분 여부</li>
 *   <li>이번 달 소비 판단</li>
 * </ol>
 */
@Component
public class MonthlyReportSummaryGenerator {

    private static final int JUDGMENT_CAUTION_THRESHOLD = 80;
    private static final int JUDGMENT_OVER_THRESHOLD = 100;

    public List<String> generate(
            BigDecimal dividendAmount,
            BigDecimal dividendChangeRate,
            int spendingRatio,
            boolean balanceSufficient
    ) {
        List<String> lines = new ArrayList<>(3);
        lines.add(dividendLine(dividendAmount, dividendChangeRate));
        lines.add(balanceLine(balanceSufficient));
        lines.add(spendingLine(spendingRatio));
        return lines;
    }

    private String dividendLine(BigDecimal dividendAmount, BigDecimal dividendChangeRate) {
        if (dividendAmount == null || dividendAmount.signum() == 0) {
            return "아직 배당 수입이 없어요. ETF 투자를 시작해보세요.";
        }
        if (dividendChangeRate == null) {
            return "배당금이 꾸준히 들어오고 있어요.";
        }
        String rate = dividendChangeRate.abs().setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        if (dividendChangeRate.signum() > 0) {
            return "배당금이 지난달보다 " + rate + "% 늘었어요.";
        }
        if (dividendChangeRate.signum() < 0) {
            return "배당금이 지난달보다 " + rate + "% 줄었어요.";
        }
        return "배당금이 지난달과 동일하게 들어왔어요.";
    }

    private String balanceLine(boolean balanceSufficient) {
        if (balanceSufficient) {
            return "다음 달도 잔액 부족 없이 지낼 수 있어요.";
        }
        return "다음 달 고정 지출 전 잔액 확인이 필요해요.";
    }

    private String spendingLine(int ratio) {
        if (ratio <= JUDGMENT_CAUTION_THRESHOLD) {
            return "소비는 목표 안에서 잘 관리되고 있어요.";
        }
        if (ratio <= JUDGMENT_OVER_THRESHOLD) {
            return "소비가 수입에 근접했어요. 지출 점검이 필요해요.";
        }
        return "이번 달 소비가 수입을 초과했어요. 지출을 점검해보세요.";
    }
}
