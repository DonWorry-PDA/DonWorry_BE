package com.sol.product.dailyprice.entity;

import com.sol.product.product.entity.FinancialProduct;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "daily_price",
        uniqueConstraints = @UniqueConstraint(columnNames = {"product_id", "price_date"}))
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "price_id")
    private Long priceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private FinancialProduct product;

    @Column(name = "price_date", nullable = false)
    private LocalDate priceDate;           // BAS_DD

    @Column(name = "closing_price", precision = 15, scale = 2)
    private BigDecimal closingPrice;       // TDD_CLSPRC

    @Column(name = "open_price", precision = 15, scale = 2)
    private BigDecimal openPrice;          // TDD_OPNPRC

    @Column(name = "high_price", precision = 15, scale = 2)
    private BigDecimal highPrice;          // TDD_HGPRC

    @Column(name = "low_price", precision = 15, scale = 2)
    private BigDecimal lowPrice;           // TDD_LWPRC

    @Column(name = "price_change", precision = 15, scale = 2)
    private BigDecimal priceChange;        // CMPPREVDD_PRC

    @Column(name = "change_rate", precision = 7, scale = 2)
    private BigDecimal changeRate;         // FLUC_RT

    @Column(name = "nav", precision = 15, scale = 2)
    private BigDecimal nav;                // NAV

    @Column(name = "trading_volume")
    private Long tradingVolume;            // ACC_TRDVOL

    @Column(name = "trading_value", precision = 20, scale = 0)
    private BigDecimal tradingValue;       // ACC_TRDVAL

    @Column(name = "market_cap", precision = 20, scale = 0)
    private BigDecimal marketCap;          // MKTCAP

    @Column(name = "net_asset_total", precision = 20, scale = 0)
    private BigDecimal netAssetTotal;      // INVSTASST_NETASST_TOTAMT

    @Column(name = "listed_shares")
    private Long listedShares;             // LIST_SHRS

    // 기초지수 관련 (ETF 전용)
    @Column(name = "index_price", precision = 15, scale = 2)
    private BigDecimal indexPrice;         // OBJ_STKPRC_IDX

    @Column(name = "index_change", precision = 15, scale = 2)
    private BigDecimal indexChange;        // CMPPREVDD_IDX

    @Column(name = "index_change_rate", precision = 7, scale = 2)
    private BigDecimal indexChangeRate;    // FLUC_RT_IDX
}
