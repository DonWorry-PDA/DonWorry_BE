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
@Table(name = "일별시세",
        uniqueConstraints = @UniqueConstraint(columnNames = {"product_id", "시세날짜"}))
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

    @Column(name = "시세날짜", nullable = false)
    private LocalDate priceDate;           // BAS_DD

    @Column(name = "종가", precision = 15, scale = 2)
    private BigDecimal closingPrice;       // TDD_CLSPRC

    @Column(name = "시가", precision = 15, scale = 2)
    private BigDecimal openPrice;          // TDD_OPNPRC

    @Column(name = "고가", precision = 15, scale = 2)
    private BigDecimal highPrice;          // TDD_HGPRC

    @Column(name = "저가", precision = 15, scale = 2)
    private BigDecimal lowPrice;           // TDD_LWPRC

    @Column(name = "전일대비", precision = 15, scale = 2)
    private BigDecimal priceChange;        // CMPPREVDD_PRC

    @Column(name = "등락률", precision = 7, scale = 2)
    private BigDecimal changeRate;         // FLUC_RT

    @Column(name = "순자산가치", precision = 15, scale = 2)
    private BigDecimal nav;                // NAV

    @Column(name = "거래량")
    private Long tradingVolume;            // ACC_TRDVOL

    @Column(name = "거래대금", precision = 20, scale = 0)
    private BigDecimal tradingValue;       // ACC_TRDVAL

    @Column(name = "시가총액", precision = 20, scale = 0)
    private BigDecimal marketCap;          // MKTCAP

    @Column(name = "순자산총액", precision = 20, scale = 0)
    private BigDecimal netAssetTotal;      // INVSTASST_NETASST_TOTAMT

    @Column(name = "상장좌수")
    private Long listedShares;             // LIST_SHRS

    // 기초지수 관련 (ETF 전용)
    @Column(name = "기초지수종가", precision = 15, scale = 2)
    private BigDecimal indexPrice;         // OBJ_STKPRC_IDX

    @Column(name = "기초지수대비", precision = 15, scale = 2)
    private BigDecimal indexChange;        // CMPPREVDD_IDX

    @Column(name = "기초지수등락률", precision = 7, scale = 2)
    private BigDecimal indexChangeRate;    // FLUC_RT_IDX
}
