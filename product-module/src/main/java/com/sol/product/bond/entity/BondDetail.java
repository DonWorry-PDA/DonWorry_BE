package com.sol.product.bond.entity;

import com.sol.product.product.entity.FinancialProduct;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "bond_detail")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BondDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "bond_detail_id")
    private Long bondDetailId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private FinancialProduct product;

    @Column(name = "ticker_code", length = 20)
    private String tickerCode;

    @Column(name = "listed_market", length = 20)
    private String listedMarket;

    @Column(name = "bond_exp_type", length = 20)
    private String bondExpType;      // BND_EXP_TP_NM (만기년수)

    @Column(name = "bond_issue_type", length = 50)
    private String bondIssueType;    // GOVBND_ISU_TP_NM (종목구분)

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}