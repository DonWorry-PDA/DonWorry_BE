package com.sol.product.etf.entity;

import com.sol.product.product.entity.FinancialProduct;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

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

    @Column(name = "asset_manager", length = 50)
    private String assetManager;

    @Column(name = "brand_name", length = 20)
    private String brandName;

    @Column(name = "retirement_pension_limit", length = 20)
    private String retirementPensionLimit;

    @Column(name = "personal_pension_available")
    private Boolean personalPensionAvailable;

    @Column(name = "latest_dividend_rate", precision = 7, scale = 2)
    private BigDecimal latestDividendRate;

    @Column(name = "annual_dividend_rate", precision = 7, scale = 2)
    private BigDecimal annualDividendRate;

    @Column(name = "high_52w", precision = 15, scale = 2)
    private BigDecimal high52w;

    @Column(name = "low_52w", precision = 15, scale = 2)
    private BigDecimal low52w;

    @Column(name = "distribution_cycle", length = 20)
    private String distributionCycle;

    @Column(name = "distribution_interval_months")
    private Integer distributionIntervalMonths;

}
