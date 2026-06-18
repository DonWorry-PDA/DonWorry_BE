package com.sol.product.etf.entity;

import com.sol.product.product.entity.FinancialProduct;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "etf_detail")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EtfDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "etf_detail_id")
    private Long etfDetailId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private FinancialProduct product;

    @Column(name = "ticker_code", length = 20, nullable = false)
    private String tickerCode;

    @Builder.Default
    @Column(name = "krx_category", nullable = false, length = 50)
    private String krxCategory = "";

    // IDX_IND_NM — 기초지수명 (예: 코스피200, S&P500 등)
    @Column(name = "benchmark_index", length = 100)
    private String benchmarkIndex;

    @Column(name = "asset_manager", length = 50)
    private String assetManager;

    // 총보수율 (TER, %) — 이 API에서 제공되지 않아 별도 입력 필요
    @Column(name = "total_expense_ratio", precision = 5, scale = 4)
    private BigDecimal totalExpenseRatio;

    @Column(name = "listing_date")
    private LocalDate listingDate;

    @Column(name = "available_account_types", length = 100)
    private String availableAccountTypes;

    // 과세유형명 (예: 배당소득세(보유기간과세))
    @Column(name = "tax_type", length = 100)
    private String taxType;

    // 과세유형 코드
    @Column(name = "tax_type_code", length = 20)
    private String taxTypeCode;

    // 변동성 등급 (예: 매우높음, 높음, 보통, 낮음, 매우낮음)
    @Column(name = "volatility_grade", length = 20)
    private String volatilityGrade;

    // 변동성 수준 (숫자 단계)
    @Column(name = "volatility_level")
    private Integer volatilityLevel;

    // SOL ETF 내부 펀드코드 (분배금 API 연동용, 예: "210930")
    @Column(name = "sol_fund_code", length = 20, unique = true)
    private String solFundCode;

    // 분배 주기 코드 (M1=월, Y4=분기, Y1=연1회, NN=무분배)
    @Column(name = "distribution_cycle", length = 5)
    private String distributionCycle;

    public void updateSolMetadata(String solFundCode, String distributionCycle) {
        this.solFundCode = solFundCode;
        this.distributionCycle = distributionCycle;
    }
}