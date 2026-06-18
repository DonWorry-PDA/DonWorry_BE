package com.sol.product.dividend.entity;

import com.sol.product.product.entity.FinancialProduct;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "dividend_history")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DividendHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "dist_id")
    private Long distId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private FinancialProduct product;

    @Column(name = "dividend_type", length = 20)
    private String dividendType;

    @Column(name = "ex_dividend_date")
    private LocalDate exDividendDate;

    @Column(name = "payment_date")
    private LocalDate paymentDate;

    @Column(name = "amount_per_unit", precision = 15, scale = 2)
    private BigDecimal amountPerUnit;
}
