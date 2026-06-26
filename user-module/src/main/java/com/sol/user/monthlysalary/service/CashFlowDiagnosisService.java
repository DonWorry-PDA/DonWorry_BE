package com.sol.user.monthlysalary.service;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.user.holding.dto.EtfHolding;
import com.sol.user.holding.repository.HoldingRepository;
import com.sol.user.monthlysalary.dto.CashFlowDiagnosisResponse;
import com.sol.user.monthlysalary.mapper.SalaryAssetMapper;
import com.sol.user.monthlysalary.repository.SalaryAssetExclusionRepository;
import com.sol.user.pension.repository.PensionRepository;
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
        // 국민연금은 제외 대상 자산이 아니므로 계좌 제외는 무관, 종목(HOLDING_*) 제외만 적용.
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

    private BigDecimal toWon(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP);
    }
}
