package com.sol.user.portfolio.mapper;

import com.sol.user.portfolio.dto.SavePlanRequest;
import com.sol.user.portfolio.dto.SavePlanResponse;
import com.sol.user.portfolio.dto.SavedPlanResponse;
import com.sol.user.portfolio.entity.SavedPortfolioPlan;
import com.sol.user.portfolio.entity.SavedPortfolioPlanItem;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class SavedPortfolioPlanMapper {

    public SavePlanResponse toSaveResponse(Long id, LocalDateTime savedAt) {
        return new SavePlanResponse(id, savedAt);
    }

    public SavedPlanResponse toSavedPlanResponse(SavedPortfolioPlan entity) {
        List<SavedPlanResponse.HoldingItem> holdings = entity.getItems().stream()
                .map(item -> new SavedPlanResponse.HoldingItem(
                        item.getProductId(),
                        item.getTicker(),
                        item.getProductName(),
                        item.getWeight(),
                        item.getTargetAmount()
                ))
                .toList();

        return new SavedPlanResponse(
                entity.getId(),
                entity.getPlanType(),
                entity.getMonthlyIncome(),
                entity.getCurrentCoverageRate(),
                entity.getTotalCoverageRate(),
                entity.getCurrentMonthlyShortfall(),
                entity.getResidualMonthlyShortfall(),
                entity.getPrincipalAmount(),
                holdings,
                entity.getSavedAt()
        );
    }

    public SavedPortfolioPlanItem toItem(SavePlanRequest.HoldingItem h) {
        return SavedPortfolioPlanItem.builder()
                .productId(h.productId())
                .ticker(h.ticker())
                .productName(h.productName())
                .weight(h.weight())
                .targetAmount(h.targetAmount())
                .build();
    }
}
