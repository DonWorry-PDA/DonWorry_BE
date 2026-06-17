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
@Table(name = "ETF상품정보")
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

    @Column(name = "종목코드", length = 20, nullable = false)
    private String tickerCode;

    @Builder.Default
    @Column(name = "krx분류", nullable = false, length = 50)
    private String krxCategory = "";

    // IDX_IND_NM — 기초지수명 (예: 코스피200, S&P500 등)
    @Column(name = "기초지수명", length = 100)
    private String benchmarkIndex;

    @Column(name = "운용사", length = 50)
    private String assetManager;

    // 총보수율 (TER, %) — 이 API에서 제공되지 않아 별도 입력 필요
    @Column(name = "총보수율", precision = 5, scale = 4)
    private BigDecimal totalExpenseRatio;

    @Column(name = "상장일")
    private LocalDate listingDate;

    @Column(name = "담을수있는계좌", length = 100)
    private String availableAccountTypes;
}