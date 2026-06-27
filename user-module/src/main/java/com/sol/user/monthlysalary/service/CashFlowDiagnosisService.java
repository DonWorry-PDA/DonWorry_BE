package com.sol.user.monthlysalary.service;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.user.holding.dto.EtfHolding;
import com.sol.user.holding.repository.HoldingRepository;
import com.sol.user.monthlysalary.dto.CashFlowDiagnosisResponse;
import com.sol.user.monthlysalary.mapper.SalaryAssetMapper;
import com.sol.user.monthlysalary.repository.SalaryAssetExclusionRepository;
import com.sol.user.pension.repository.PensionRepository;
import com.sol.user.portfolio.config.PortfolioConstants;
import com.sol.user.portfolio.infra.rest.ProductBatchClient;
import com.sol.user.user.repository.UserRepository;
import com.sol.user.usergoal.entity.UserGoal;
import com.sol.user.usergoal.repository.UserGoalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CashFlowDiagnosisService {

    private static final String NATIONAL_PENSION_TYPE = "NATIONAL";

    private final PensionRepository pensionRepository;
    private final UserGoalRepository userGoalRepository;
    private final HoldingRepository holdingRepository;
    private final UserRepository userRepository;
    private final ProductBatchClient productBatchClient;
    private final SalaryAssetExclusionRepository salaryAssetExclusionRepository;
    private final SalaryAssetMapper salaryAssetMapper;

    @Transactional(readOnly = true)
    public CashFlowDiagnosisResponse diagnose(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new BaseException(ErrorCode.USER_NOT_FOUND);
        }

        // 실수령(net) 반영: 국민연금은 면세 근사(0%), 분배금은 15.4% 원천징수 차감.
        BigDecimal nationalPension = netNationalPension(pensionRepository
                .findMonthlyAmount(userId, NATIONAL_PENSION_TYPE)
                .orElse(BigDecimal.ZERO));

        BigDecimal dividendIncome = netFinancial(calcMonthlyDividendIncome(userId));

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
        Set<Long> excludedHoldingIds = salaryAssetMapper.extractHoldingIds(
                salaryAssetExclusionRepository.findAssetKeysByUserId(userId));
        List<EtfHolding> holdings = holdingRepository.findAllHoldingsByUserId(userId).stream()
                .filter(holding -> !excludedHoldingIds.contains(holding.getHoldingId()))
                .toList();
        if (holdings.isEmpty()) {
            return BigDecimal.ZERO;
        }
        List<Long> productIds = holdings.stream().map(EtfHolding::getProductId).toList();
        Map<Long, BigDecimal> monthlyDividendMap = productBatchClient.fetchEtfMonthlyDividends(productIds);

        return holdings.stream()
                .map(h -> monthlyDividendMap.getOrDefault(h.getProductId(), BigDecimal.ZERO)
                        .multiply(h.getQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
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
