package com.sol.user.portfolio.mapper;

import com.sol.user.portfolio.dto.SavePlanRequest;
import com.sol.user.portfolio.dto.SavePlanResponse;
import com.sol.user.portfolio.dto.SavedPlanResponse;
import com.sol.user.portfolio.entity.SavedPortfolioPlan;
import com.sol.user.portfolio.entity.SavedPortfolioPlanItem;
import com.sol.user.portfolio.type.PlanType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SavedPortfolioPlanMapperTest {

    private final SavedPortfolioPlanMapper mapper = new SavedPortfolioPlanMapper();

    @Test
    void toSaveResponse_savedAt_그대로_반환() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 30, 12, 0, 0);

        SavePlanResponse response = mapper.toSaveResponse(now);

        assertThat(response.savedAt()).isEqualTo(now);
    }

    @Test
    void toSavedPlanResponse_헤더_필드_전체_매핑() {
        LocalDateTime savedAt = LocalDateTime.of(2026, 6, 30, 12, 0, 0);
        SavedPortfolioPlan entity = mock(SavedPortfolioPlan.class);
        when(entity.getPlanType()).thenReturn(PlanType.STABLE);
        when(entity.getMonthlyIncome()).thenReturn(new BigDecimal("1680000"));
        when(entity.getCurrentCoverageRate()).thenReturn(new BigDecimal("59.00"));
        when(entity.getTotalCoverageRate()).thenReturn(new BigDecimal("84.00"));
        when(entity.getCurrentMonthlyShortfall()).thenReturn(new BigDecimal("900000"));
        when(entity.getResidualMonthlyShortfall()).thenReturn(BigDecimal.ZERO);
        when(entity.getPrincipalAmount()).thenReturn(new BigDecimal("200000000"));
        when(entity.getSavedAt()).thenReturn(savedAt);
        when(entity.getItems()).thenReturn(List.of());

        SavedPlanResponse response = mapper.toSavedPlanResponse(entity);

        assertThat(response.planType()).isEqualTo(PlanType.STABLE);
        assertThat(response.monthlyIncome()).isEqualByComparingTo("1680000");
        assertThat(response.currentCoverageRate()).isEqualByComparingTo("59.00");
        assertThat(response.totalCoverageRate()).isEqualByComparingTo("84.00");
        assertThat(response.currentMonthlyShortfall()).isEqualByComparingTo("900000");
        assertThat(response.residualMonthlyShortfall()).isEqualByComparingTo("0");
        assertThat(response.principalAmount()).isEqualByComparingTo("200000000");
        assertThat(response.savedAt()).isEqualTo(savedAt);
    }

    @Test
    void toSavedPlanResponse_종목_목록_매핑() {
        SavedPortfolioPlan entity = mock(SavedPortfolioPlan.class);
        when(entity.getPlanType()).thenReturn(PlanType.STABLE);
        when(entity.getMonthlyIncome()).thenReturn(BigDecimal.ZERO);
        when(entity.getCurrentCoverageRate()).thenReturn(BigDecimal.ZERO);
        when(entity.getTotalCoverageRate()).thenReturn(BigDecimal.ZERO);
        when(entity.getCurrentMonthlyShortfall()).thenReturn(BigDecimal.ZERO);
        when(entity.getResidualMonthlyShortfall()).thenReturn(BigDecimal.ZERO);
        when(entity.getPrincipalAmount()).thenReturn(BigDecimal.ZERO);
        when(entity.getSavedAt()).thenReturn(LocalDateTime.now());

        SavedPortfolioPlanItem item = mock(SavedPortfolioPlanItem.class);
        when(item.getProductId()).thenReturn(101L);
        when(item.getTicker()).thenReturn("069500");
        when(item.getProductName()).thenReturn("KODEX 200");
        when(item.getWeight()).thenReturn(new BigDecimal("0.60"));
        when(item.getTargetAmount()).thenReturn(new BigDecimal("120000000"));
        when(entity.getItems()).thenReturn(List.of(item));

        SavedPlanResponse response = mapper.toSavedPlanResponse(entity);

        assertThat(response.holdings()).hasSize(1);
        SavedPlanResponse.HoldingItem h = response.holdings().get(0);
        assertThat(h.productId()).isEqualTo(101L);
        assertThat(h.ticker()).isEqualTo("069500");
        assertThat(h.productName()).isEqualTo("KODEX 200");
        assertThat(h.weight()).isEqualByComparingTo("0.60");
        assertThat(h.targetAmount()).isEqualByComparingTo("120000000");
    }

    @Test
    void toItem_요청_항목을_엔티티로_변환() {
        SavePlanRequest.HoldingItem h = new SavePlanRequest.HoldingItem(
                101L, "069500", "KODEX 200",
                new BigDecimal("0.60"), new BigDecimal("120000000"));

        SavedPortfolioPlanItem item = mapper.toItem(h);

        assertThat(item.getProductId()).isEqualTo(101L);
        assertThat(item.getTicker()).isEqualTo("069500");
        assertThat(item.getProductName()).isEqualTo("KODEX 200");
        assertThat(item.getWeight()).isEqualByComparingTo("0.60");
        assertThat(item.getTargetAmount()).isEqualByComparingTo("120000000");
        assertThat(item.getSavedPlan()).isNull();
    }
}
