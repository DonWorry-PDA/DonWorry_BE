package com.sol.product.dailyprice.entity;

import com.sol.product.product.entity.FinancialProduct;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "일별시세")
@Getter
@NoArgsConstructor
public class DailyPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "price_id")
    private Long priceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private FinancialProduct product;

    @Column(name = "시세날짜", nullable = false)
    private LocalDate priceDate;

    @Column(name = "종가", precision = 15, scale = 2)
    private BigDecimal closingPrice;

    @Column(name = "기준가", precision = 15, scale = 2)
    private BigDecimal nav;
}
