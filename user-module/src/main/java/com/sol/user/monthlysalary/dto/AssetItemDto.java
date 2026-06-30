package com.sol.user.monthlysalary.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class AssetItemDto {
    private String assetKey;
    private String name;
    private String description;
    private BigDecimal amount;
    private boolean excluded;
    private Deployability deployability;

    public enum Deployability {
        /** 즉시 ETF 매수 가능한 자유현금·ETF 보유. */
        FREE,
        /** 정기예금(DEPOSIT) — 약정이라 매수 불가, 이자 수익만 기여. */
        PINNED_SAFE,
        /** IRP·연금저축 — 55세 인출 제약, 연금 사이드카 트랙. */
        RESTRICTED_PENSION,
        /** 개별주식 — 청산 전제라 월급 재료에서 제외. */
        EXCLUDED_STOCK
    }
}
