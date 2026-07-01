package com.sol.user.monthlysalary.service;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.user.cashflow.service.MonthlyCashFlowProjection;
import com.sol.user.holding.service.EtfDividendCalculator;
import com.sol.user.monthlysalary.dto.CashFlowDiagnosisResponse;
import com.sol.user.monthlysalary.mapper.SalaryAssetMapper;
import com.sol.user.monthlysalary.repository.SalaryAssetExclusionRepository;
import com.sol.user.pension.repository.PensionRepository;
import com.sol.user.portfolio.config.PortfolioConstants;
import com.sol.user.user.entity.User;
import com.sol.user.user.repository.UserRepository;
import com.sol.user.usergoal.entity.UserGoal;
import com.sol.user.usergoal.repository.UserGoalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CashFlowDiagnosisService {

    private static final String NATIONAL_PENSION_TYPE = "NATIONAL";
    private static final String INTEREST_EVENT_TYPE = "INTEREST";

    private final PensionRepository pensionRepository;
    private final UserGoalRepository userGoalRepository;
    private final UserRepository userRepository;
    private final SalaryAssetExclusionRepository salaryAssetExclusionRepository;
    private final SalaryAssetMapper salaryAssetMapper;
    private final EtfDividendCalculator etfDividendCalculator;
    private final MonthlyCashFlowProjection projection;

    @Transactional(readOnly = true)
    public CashFlowDiagnosisResponse diagnose(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

        // 국민연금은 '현재 수령 중'일 때만 계상한다 — 미수령(재직 등) 유저는 0.
        // 자산분석 income(AssetIncomeService)과 동일 규칙: 플래그를 무시하고 예상연금을 넣던 버그 수정.
        BigDecimal grossPension = Boolean.TRUE.equals(user.getNationalPensionReceiving())
                ? pensionRepository.findMonthlyAmount(userId, NATIONAL_PENSION_TYPE).orElse(BigDecimal.ZERO)
                : BigDecimal.ZERO;
        // 실수령(net) 반영: 국민연금은 면세 근사(0%), 분배금은 15.4% 원천징수 차감.
        BigDecimal nationalPension = netNationalPension(grossPension);

        BigDecimal dividendIncome = netFinancial(calcMonthlyDividendIncome(userId));

        // 예금 이자도 생활비로 쓸 수 있는 실수령 현금흐름이라 셈에 넣는다 — 리포트·홈 수입 집계엔
        // 이미 잡히는데 여기서만 빠져 "현재 수입"이 화면마다 다르게 보이던 버그.
        // 정기수입 투영은 캘린더·리포트와 같은 공용 소스(MonthlyCashFlowProjection)를 쓴다.
        BigDecimal interestIncome = netFinancial(
                projection.project(userId, YearMonth.now()).sumEventType(INTEREST_EVENT_TYPE));

        BigDecimal targetMonthlyLivingCost = userGoalRepository.findByUserUserId(userId)
                .map(UserGoal::getMonthlyTargetLivingCost)
                .orElse(BigDecimal.ZERO);

        BigDecimal monthlyCashFlow = nationalPension.add(dividendIncome).add(interestIncome);
        BigDecimal monthlyShortfall = targetMonthlyLivingCost.subtract(monthlyCashFlow).max(BigDecimal.ZERO);

        // 반올림은 응답 단계에서만 (원 단위). 중간 계산은 전체 정밀도 유지.
        return CashFlowDiagnosisResponse.builder()
                .monthlyCashFlow(toWon(monthlyCashFlow))
                .nationalPension(toWon(nationalPension))
                .dividendIncome(toWon(dividendIncome))
                .interestIncome(toWon(interestIncome))
                .targetMonthlyLivingCost(toWon(targetMonthlyLivingCost))
                .monthlyShortfall(toWon(monthlyShortfall))
                .shortfallExists(monthlyShortfall.compareTo(BigDecimal.ZERO) > 0)
                .build();
    }

    private BigDecimal calcMonthlyDividendIncome(Long userId) {
        // 월급 만들기에서 제외한 보유종목은 분배금 산출에서도 뺀다(선택UI #115 死선 해소).
        // 계좌(ACCOUNT_*) 제외는 예수금(cash)만 빼는데 분배금은 보유종목에서만 나오므로 무관 —
        // AssetAggregator와 동일하게 계좌·종목 제외는 독립이라 종목(HOLDING_*) 제외만 적용한다.
        // 분배금 단가·합산은 단일 출처(EtfDividendCalculator)에서 가져오고, 여기선 제외만 넘긴다(#303).
        Set<Long> excludedHoldingIds = salaryAssetMapper.extractHoldingIds(
                salaryAssetExclusionRepository.findAssetKeysByUserId(userId));
        // 현금흐름 진단은 연금·비연금 구분 없이 전체 분배금을 본다(생활비 충당 관점).
        return etfDividendCalculator.monthlyDividendBreakdown(userId, excludedHoldingIds).totalGross();
    }

    /** 이자·배당소득 원천징수(15.4%) 차감 후 실수령. */
    private BigDecimal netFinancial(BigDecimal value) {
        return value.multiply(BigDecimal.ONE.subtract(PortfolioConstants.WITHHOLDING_FINANCIAL));
    }

    /** 국민연금 실수령 — 연금소득공제로 면세 근사(현재 0%). 향후 종합과세 정밀화 시 세율만 조정. */
    private BigDecimal netNationalPension(BigDecimal value) {
        return value.multiply(BigDecimal.ONE.subtract(PortfolioConstants.NATIONAL_PENSION_TAX_RATE));
    }

    private BigDecimal toWon(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP);
    }
}
