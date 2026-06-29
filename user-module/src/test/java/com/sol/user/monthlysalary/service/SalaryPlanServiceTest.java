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
import com.sol.user.portfolio.dto.EtfInfo;
import com.sol.user.portfolio.provider.EtfPoolProvider;
import com.sol.user.stability.service.LifeStabilityService;
import com.sol.user.user.entity.User;
import com.sol.user.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalaryPlanServiceTest {

    private static final Long USER_ID = 1L;

    @Mock SalaryPlanRepository salaryPlanRepository;
    @Mock UserRepository userRepository;
    @Mock HoldingRepository holdingRepository;
    @Mock EtfPoolProvider etfPoolProvider;
    @Mock LifeStabilityService lifeStabilityService;

    @InjectMocks SalaryPlanService salaryPlanService;

    @Test
    void 운용현황_ACTIVE_없으면_빈응답_최초진입_분기() {
        when(salaryPlanRepository.findWithItemsByUserUserIdAndStatus(USER_ID, SalaryPlan.STATUS_ACTIVE))
                .thenReturn(Optional.empty());
        when(salaryPlanRepository.findTopByUserUserIdOrderByCreatedAtDesc(USER_ID))
                .thenReturn(Optional.empty());

        SalaryPlanStatusResponse response = salaryPlanService.getStatus(USER_ID);

        assertThat(response.hasPlan()).isFalse();
        assertThat(response.holdings()).isNull();
    }

    @Test
    void getStatusReturnsLatestPlanWhenActivePlanDoesNotExist() {
        SalaryPlan plan = SalaryPlan.active(mock(User.class), "STABLE",
                new BigDecimal("1680000"), new BigDecimal("84.00"), new BigDecimal("2000000"));
        plan.addItem(item(101L, "SOL 국고채", "SAFE", new BigDecimal("10000000")));
        plan.supersede();
        when(salaryPlanRepository.findWithItemsByUserUserIdAndStatus(USER_ID, SalaryPlan.STATUS_ACTIVE))
                .thenReturn(Optional.empty());
        when(salaryPlanRepository.findTopByUserUserIdOrderByCreatedAtDesc(USER_ID))
                .thenReturn(Optional.of(plan));
        when(holdingRepository.findHoldingsWithAccountTypeByUserId(USER_ID))
                .thenReturn(List.of());

        SalaryPlanStatusResponse response = salaryPlanService.getStatus(USER_ID);

        assertThat(response.hasPlan()).isTrue();
        assertThat(response.displayName()).isEqualTo("안정 월급형");
        assertThat(response.holdings()).hasSize(1);
        assertThat(response.totalCurrentEval()).isEqualByComparingTo("0");
    }

    @Test
    void 운용현황_진행률은_종목별_BROKERAGE_평가액_대비_목표() {
        SalaryPlan plan = SalaryPlan.active(mock(User.class), "STABLE",
                new BigDecimal("1680000"), new BigDecimal("84.00"), new BigDecimal("2000000"));
        plan.addItem(item(101L, "SOL 국고채3년", "SAFE", new BigDecimal("10000000")));
        plan.addItem(item(102L, "TIGER 미국S&P500", "RISK", new BigDecimal("5000000")));
        when(salaryPlanRepository.findWithItemsByUserUserIdAndStatus(USER_ID, SalaryPlan.STATUS_ACTIVE))
                .thenReturn(Optional.of(plan));
        // 101: 4,000,000 보유(40%) / 102: 미보유(0%). IRP 평가액은 BROKERAGE 필터로 제외.
        // 모킹 객체는 when() 밖에서 먼저 생성 — 중첩 스터빙(UnfinishedStubbingException) 회피.
        HoldingWithProduct brokerageHolding = holding(101L, "4000000", "BROKERAGE");
        HoldingWithProduct irpHolding = holding(102L, "9999999", "IRP");
        when(holdingRepository.findHoldingsWithAccountTypeByUserId(USER_ID))
                .thenReturn(List.of(brokerageHolding, irpHolding));

        SalaryPlanStatusResponse response = salaryPlanService.getStatus(USER_ID);

        assertThat(response.hasPlan()).isTrue();
        assertThat(response.displayName()).isEqualTo("안정 월급형");
        assertThat(response.holdings()).hasSize(2);

        SalaryPlanStatusResponse.HoldingStatus safe = response.holdings().get(0);
        assertThat(safe.currentEval()).isEqualByComparingTo("4000000");
        assertThat(safe.achievedRate()).isEqualByComparingTo("40.00");
        assertThat(safe.remainingToBuy()).isEqualByComparingTo("6000000");

        SalaryPlanStatusResponse.HoldingStatus risk = response.holdings().get(1);
        assertThat(risk.currentEval()).isEqualByComparingTo("0");
        assertThat(risk.remainingToBuy()).isEqualByComparingTo("5000000");

        // 합계: 4,000,000 / 15,000,000 = 26.67%
        assertThat(response.totalTargetAmount()).isEqualByComparingTo("15000000");
        assertThat(response.totalCurrentEval()).isEqualByComparingTo("4000000");
        assertThat(response.totalAchievedRate()).isEqualByComparingTo("26.67");
    }

    @Test
    void 운용현황_진행률은_초과매수여도_100에서_캡된다() {
        SalaryPlan plan = SalaryPlan.active(mock(User.class), "STABLE",
                new BigDecimal("1680000"), new BigDecimal("84.00"), new BigDecimal("2000000"));
        plan.addItem(item(101L, "SOL 국고채3년", "SAFE", new BigDecimal("10000000")));
        when(salaryPlanRepository.findWithItemsByUserUserIdAndStatus(USER_ID, SalaryPlan.STATUS_ACTIVE))
                .thenReturn(Optional.of(plan));
        // 목표 10,000,000 대비 15,000,000 보유(150%) → 100 캡, remainingToBuy 0.
        HoldingWithProduct overBought = holding(101L, "15000000", "BROKERAGE");
        when(holdingRepository.findHoldingsWithAccountTypeByUserId(USER_ID))
                .thenReturn(List.of(overBought));

        SalaryPlanStatusResponse response = salaryPlanService.getStatus(USER_ID);

        assertThat(response.holdings().get(0).achievedRate()).isEqualByComparingTo("100.00");
        assertThat(response.holdings().get(0).remainingToBuy()).isEqualByComparingTo("0");
        assertThat(response.holdings().get(0).currentEval()).isEqualByComparingTo("15000000");
        // 합계도 min(보유,목표) 기준이라 100에서 캡.
        assertThat(response.totalAchievedRate()).isEqualByComparingTo("100.00");
    }

    @Test
    void 확정_검증_ETF풀_미소속_productId는_거부() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(mock(User.class)));
        when(etfPoolProvider.getPool()).thenReturn(List.of(etf(101L), etf(102L)));

        SalaryPlanConfirmRequest request = request(holdingItem(999L, new BigDecimal("1000000")));

        assertThatThrownBy(() -> salaryPlanService.confirm(USER_ID, request))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void 확정_검증_targetAmount_0이하는_거부() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(mock(User.class)));
        when(etfPoolProvider.getPool()).thenReturn(List.of(etf(101L)));

        SalaryPlanConfirmRequest request = request(holdingItem(101L, BigDecimal.ZERO));

        assertThatThrownBy(() -> salaryPlanService.confirm(USER_ID, request))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void 확정_없는_사용자는_USER_NOT_FOUND() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        SalaryPlanConfirmRequest request = request(holdingItem(101L, new BigDecimal("1000000")));

        assertThatThrownBy(() -> salaryPlanService.confirm(USER_ID, request))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void confirmRecalculatesLifeStabilityAfterSavingActivePlan() {
        User user = mock(User.class);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(etfPoolProvider.getPool()).thenReturn(List.of(etf(101L)));
        SalaryPlanConfirmRequest request = request(holdingItem(101L, new BigDecimal("1000000")));

        salaryPlanService.confirm(USER_ID, request);

        verify(lifeStabilityService).recalculateFromUserDataIfReady(USER_ID);
    }

    private SalaryPlanItem item(Long productId, String name, String role, BigDecimal target) {
        return SalaryPlanItem.builder()
                .productId(productId)
                .productName(name)
                .bucketRole(role)
                .accountType("BROKERAGE")
                .targetAmount(target)
                .build();
    }

    private HoldingWithProduct holding(Long productId, String eval, String accountType) {
        HoldingWithProduct h = mock(HoldingWithProduct.class);
        lenient().when(h.getProductId()).thenReturn(productId);
        lenient().when(h.getEvaluationAmount()).thenReturn(new BigDecimal(eval));
        lenient().when(h.getAccountType()).thenReturn(accountType);
        return h;
    }

    private EtfInfo etf(Long productId) {
        return new EtfInfo(productId, "T" + productId, "ETF" + productId, 5,
                BigDecimal.ZERO, "MONTHLY", null, null);
    }

    private SalaryPlanConfirmRequest request(SalaryPlanConfirmRequest.HoldingItem... items) {
        return new SalaryPlanConfirmRequest("STABLE", new BigDecimal("2000000"),
                new BigDecimal("1680000"), List.of(items));
    }

    private SalaryPlanConfirmRequest.HoldingItem holdingItem(Long productId, BigDecimal targetAmount) {
        return new SalaryPlanConfirmRequest.HoldingItem(productId, "상품" + productId, "SAFE",
                "BROKERAGE", new BigDecimal("1.00"), targetAmount, null);
    }
}
