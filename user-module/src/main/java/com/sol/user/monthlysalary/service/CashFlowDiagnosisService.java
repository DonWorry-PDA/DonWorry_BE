package com.sol.user.monthlysalary.service;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.user.holding.service.EtfDividendCalculator;
import com.sol.user.monthlysalary.dto.CashFlowDiagnosisResponse;
import com.sol.user.monthlysalary.mapper.SalaryAssetMapper;
import com.sol.user.monthlysalary.repository.SalaryAssetExclusionRepository;
import com.sol.user.pension.repository.PensionRepository;
import com.sol.user.user.repository.UserRepository;
import com.sol.user.usergoal.entity.UserGoal;
import com.sol.user.usergoal.repository.UserGoalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CashFlowDiagnosisService {

    private static final String NATIONAL_PENSION_TYPE = "NATIONAL";

    private final PensionRepository pensionRepository;
    private final UserGoalRepository userGoalRepository;
    private final UserRepository userRepository;
    private final SalaryAssetExclusionRepository salaryAssetExclusionRepository;
    private final SalaryAssetMapper salaryAssetMapper;
    private final EtfDividendCalculator etfDividendCalculator;

    @Transactional(readOnly = true)
    public CashFlowDiagnosisResponse diagnose(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new BaseException(ErrorCode.USER_NOT_FOUND);
        }

        // 세전(gross) 기준: 홈·월간리포트와 동일 기준으로 화면 간 정합한다.
        BigDecimal nationalPension = pensionRepository
                .findMonthlyAmount(userId, NATIONAL_PENSION_TYPE)
                .orElse(BigDecimal.ZERO);

        BigDecimal dividendIncome = calcMonthlyDividendIncome(userId);

        BigDecimal targetMonthlyLivingCost = userGoalRepository.findByUserUserId(userId)
                .map(UserGoal::getMonthlyTargetLivingCost)
                .orElse(BigDecimal.ZERO);

        BigDecimal monthlyCashFlow = nationalPension.add(dividendIncome);
        BigDecimal monthlyShortfall = targetMonthlyLivingCost.subtract(monthlyCashFlow).max(BigDecimal.ZERO);

        // 반올림은 응답 단계에서만 (원 단위). 중간 계산은 전체 정밀도 유지.
        return CashFlowDiagnosisResponse.builder()
                .monthlyCashFlow(toWon(monthlyCashFlow))
                .nationalPension(toWon(nationalPension))
                .dividendIncome(toWon(dividendIncome))
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

    private BigDecimal toWon(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP);
    }
}
