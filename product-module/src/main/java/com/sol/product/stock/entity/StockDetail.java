package com.sol.product.stock.entity;

import com.sol.product.product.entity.FinancialProduct;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "stock_detail")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stock_detail_id")
    private Long stockDetailId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private FinancialProduct product;

    @Column(name = "ticker_code", length = 20)
    private String tickerCode;

    @Column(name = "listed_market", length = 20)
    private String listedMarket;

    @Column(name = "dividend_yield", precision = 5, scale = 2)
    private BigDecimal dividendYield;

    @Column(name = "sector", length = 50)
    private String sector;

    @Column(name = "available_account_types", length = 100)
    private String availableAccountTypes;
}
