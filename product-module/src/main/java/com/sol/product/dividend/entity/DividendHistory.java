package com.sol.product.dividend.entity;

import com.sol.product.product.entity.FinancialProduct;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "배당이력")
@Getter
@NoArgsConstructor
public class DividendHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "dist_id")
    private Long distId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private FinancialProduct product;

    @Column(name = "배당종류", length = 20)
    private String dividendType;

    @Column(name = "배당락일")
    private LocalDate exDividendDate;

    @Column(name = "지급일")
    private LocalDate paymentDate;

    @Column(name = "주당좌당금액", precision = 15, scale = 2)
    private BigDecimal amountPerUnit;
}
