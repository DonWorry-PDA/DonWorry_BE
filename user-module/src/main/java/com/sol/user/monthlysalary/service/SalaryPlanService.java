package com.sol.user.monthlysalary.service;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.user.holding.dto.HoldingWithProduct;
import com.sol.user.holding.repository.HoldingRepository;
import com.sol.user.monthlysalary.dto.SalaryPlanConfirmRequest;
import com.sol.user.monthlysalary.dto.SalaryPlanStatusResponse;
import com.sol.user.monthlysalary.entity.SalaryPlan;
import com.sol.user.monthlysalary.entity.SalaryPlanItem;
import com.sol.user.monthlysalary.repository.SalaryPlanRepository;
import com.sol.user.monthlysalary.type.GuidanceAction;
import com.sol.user.monthlysalary.type.ReentryEmphasis;
import com.sol.user.portfolio.dto.EtfInfo;
import com.sol.user.portfolio.provider.EtfPoolProvider;
import com.sol.user.stability.service.LifeStabilityService;
import com.sol.user.user.entity.User;
import com.sol.user.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 월급 만들기 확정 plan 도입 — 기이용자(매수 완료) 분기의 본체.
 * 확정(write)은 매수 완료 스냅샷 저장만(재계산 없음), 운용현황(read)은 plan 종목별 진행률을 live 보유와 비교해 산출.
 */
@Service
@RequiredArgsConstructor
public class SalaryPlanService {

    private static final int RATE_SCALE = 2;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    // 보유 평가액은 매수 실행 대상인 BROKERAGE 계좌만 — 추천 existingEval과 동일 기준. IRP는 범위 밖(#별도이슈).
    private static final String BROKERAGE = "BROKERAGE";

    private final SalaryPlanRepository salaryPlanRepository;
    private final UserRepository userRepository;
    private final HoldingRepository holdingRepository;
    private final EtfPoolProvider etfPoolProvider;
    private final LifeStabilityService lifeStabilityService;

    /**
     * 확정 — 기존 ACTIVE를 SUPERSEDED 처리 후 신규 ACTIVE INSERT(1트랜잭션).
     * 검증은 재계산 없이 가능한 2개만: productId가 ETF 풀 소속 + targetAmount > 0(결정 2-1).
     */
    @Transactional
    public void confirm(Long userId, SalaryPlanConfirmRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

        validateHoldings(request.holdings());

        // 기존 ACTIVE 비활성화 후 flush — UPDATE(active_user_id=NULL)를 신규 INSERT보다 먼저 보내
        // active_user_id UNIQUE 충돌을 막는다(Hibernate는 기본적으로 INSERT를 UPDATE보다 먼저 큐잉).
        salaryPlanRepository.findByUserUserIdAndStatus(userId, SalaryPlan.STATUS_ACTIVE)
                .ifPresent(SalaryPlan::supersede);
        salaryPlanRepository.flush();

        BigDecimal coverageRate = coverageRate(request.expectedMonthlySalary(), request.targetMonthlyLivingCost());
        SalaryPlan plan = SalaryPlan.active(user, request.planType(), request.expectedMonthlySalary(),
                coverageRate, request.targetMonthlyLivingCost());

        for (SalaryPlanConfirmRequest.HoldingItem h : request.holdings()) {
            plan.addItem(SalaryPlanItem.builder()
                    .productId(h.productId())
                    .productName(h.productName())
                    .bucketRole(h.bucketRole())
                    .accountType(h.accountType())
                    .weight(h.weight())
                    .targetAmount(h.targetAmount())
                    .productContribution(h.productContribution())
                    .build());
        }

        salaryPlanRepository.save(plan);
        lifeStabilityService.recalculateFromUserDataIfReady(userId);
    }

    /** 운용현황 — ACTIVE plan을 우선 조회하고, 없으면 최신 plan 이력으로 종목별 진행률을 조립한다. */
    @Transactional(readOnly = true)
    public SalaryPlanStatusResponse getStatus(Long userId) {
        SalaryPlan plan = salaryPlanRepository
                .findWithItemsByUserUserIdAndStatus(userId, SalaryPlan.STATUS_ACTIVE)
                .or(() -> salaryPlanRepository.findTopByUserUserIdOrderByCreatedAtDesc(userId))
                .orElse(null);
        if (plan == null) {
            return SalaryPlanStatusResponse.empty();
        }

        Map<Long, BigDecimal> currentEvalByProductId = brokerageEvalByProductId(userId);

        List<SalaryPlanStatusResponse.HoldingStatus> holdings = plan.getItems().stream()
                .map(item -> toHoldingStatus(item, currentEvalByProductId))
                .toList();

        BigDecimal totalTarget = holdings.stream()
                .map(SalaryPlanStatusResponse.HoldingStatus::targetAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCurrent = holdings.stream()
                .map(SalaryPlanStatusResponse.HoldingStatus::currentEval)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // 합계 진행률 분자는 종목별 min(보유,목표) — 한 종목 초과매수가 다른 종목 미달을 가리지 않게.
        BigDecimal achievedTowardTarget = holdings.stream()
                .map(h -> h.currentEval().min(h.targetAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return SalaryPlanStatusResponse.builder()
                .hasPlan(true)
                .planType(plan.getPlanType())
                .displayName(resolveDisplayName(plan.getPlanType()))
                .expectedMonthlySalary(plan.getExpectedMonthlySalary())
                .targetMonthlyLivingCost(plan.getTargetMonthlyLivingCost())
                .livingCostCoverageRate(plan.getLivingCostCoverageRate())
                .totalTargetAmount(totalTarget)
                .totalCurrentEval(totalCurrent)
                .totalAchievedRate(progressRate(achievedTowardTarget, totalTarget))
                .createdAt(plan.getCreatedAt())
                .holdings(holdings)
                .reentryGuidance(buildReentryGuidance(plan.getLivingCostCoverageRate()))
                .build();
    }

    /**
     * 재진입 안내 — 저장된 충족률(확정 당시 값)로 강조만 결정한다. 두 선택지는 항상 제시.
     * 충족률 ≥ 100%면 생활비 상향을 강조, 그 외엔 중립. 정밀 충족은 "다시 설계하기→재추천"에서 확정된다.
     */
    private SalaryPlanStatusResponse.ReentryGuidance buildReentryGuidance(BigDecimal coverageRate) {
        ReentryEmphasis emphasis = coverageRate != null && coverageRate.compareTo(HUNDRED) >= 0
                ? ReentryEmphasis.INCREASE_LIVING_COST
                : ReentryEmphasis.NEUTRAL;
        return new SalaryPlanStatusResponse.ReentryGuidance(emphasis, List.of(
                SalaryPlanStatusResponse.GuidanceOption.of(GuidanceAction.INCREASE_LIVING_COST),
                SalaryPlanStatusResponse.GuidanceOption.of(GuidanceAction.RETAKE_SURVEY)));
    }

    private SalaryPlanStatusResponse.HoldingStatus toHoldingStatus(
            SalaryPlanItem item, Map<Long, BigDecimal> currentEvalByProductId) {
        BigDecimal target = item.getTargetAmount() == null ? BigDecimal.ZERO : item.getTargetAmount();
        BigDecimal current = currentEvalByProductId.getOrDefault(item.getProductId(), BigDecimal.ZERO);
        BigDecimal remaining = target.subtract(current).max(BigDecimal.ZERO);
        return SalaryPlanStatusResponse.HoldingStatus.builder()
                .productId(item.getProductId())
                .productName(item.getProductName())
                .bucketRole(item.getBucketRole())
                .targetAmount(target)
                .currentEval(current)
                .achievedRate(progressRate(current, target))
                .remainingToBuy(remaining)
                .productContribution(item.getProductContribution())
                .build();
    }

    /** live holding 평가액 — BROKERAGE만 productId별 합산(추천 existingEval과 동일 기준). IRP는 범위 밖(#별도이슈). */
    private Map<Long, BigDecimal> brokerageEvalByProductId(Long userId) {
        return holdingRepository.findHoldingsWithAccountTypeByUserId(userId).stream()
                .filter(h -> BROKERAGE.equals(h.getAccountType()))
                .collect(Collectors.toMap(
                        HoldingWithProduct::getProductId,
                        h -> h.getEvaluationAmount() == null ? BigDecimal.ZERO : h.getEvaluationAmount(),
                        BigDecimal::add
                ));
    }

    private void validateHoldings(List<SalaryPlanConfirmRequest.HoldingItem> holdings) {
        Set<Long> poolProductIds = etfPoolProvider.getPool().stream()
                .map(EtfInfo::productId)
                .collect(Collectors.toSet());
        for (SalaryPlanConfirmRequest.HoldingItem h : holdings) {
            if (!poolProductIds.contains(h.productId())) {
                throw new BaseException(ErrorCode.INVALID_INPUT);
            }
            if (h.targetAmount() == null || h.targetAmount().signum() <= 0) {
                throw new BaseException(ErrorCode.INVALID_INPUT);
            }
        }
    }

    private String resolveDisplayName(String planType) {
        return switch (planType) {
            case "STABLE" -> "안정 월급형";
            case "BALANCED" -> "균형 월급형";
            case "LIQUIDITY" -> "유동성 확보형";
            default -> planType;
        };
    }

    /** 진행률 — rate를 100%에서 캡(초과매수해도 바가 100을 넘지 않도록). 충당률엔 쓰지 않음. */
    private BigDecimal progressRate(BigDecimal numerator, BigDecimal denominator) {
        return rate(numerator, denominator).min(HUNDRED.setScale(RATE_SCALE));
    }

    /** numerator / denominator × 100 (%). denominator가 0/null이면 0. */
    private BigDecimal rate(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.signum() <= 0) {
            return BigDecimal.ZERO.setScale(RATE_SCALE);
        }
        return numerator.multiply(HUNDRED).divide(denominator, RATE_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal coverageRate(BigDecimal monthlyIncome, BigDecimal targetLivingCost) {
        return rate(monthlyIncome, targetLivingCost);
    }
}
