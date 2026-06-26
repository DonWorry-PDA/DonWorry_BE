package com.sol.user.asset.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class AssetScheduleResponse {

    private List<AssetScheduleEvent> events;

    @Getter
    @Builder
    public static class AssetScheduleEvent {
        private LocalDate date;
        private String type;
        private String label;
        private BigDecimal amount;
        private boolean estimated;
    }
}
