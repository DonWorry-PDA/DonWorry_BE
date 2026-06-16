package com.sol.product.product.entity;

import com.sol.product.assetclass.entity.AssetClass;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "금융상품")
@Getter
@NoArgsConstructor
public class FinancialProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    private Long productId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_class_id", nullable = false)
    private AssetClass assetClass;

    @Column(name = "상품유형", nullable = false, length = 20)
    private String productType;

    @Column(name = "상품명", nullable = false, length = 200)
    private String productName;

    @Column(name = "통화", length = 10, columnDefinition = "VARCHAR(10) DEFAULT 'KRW'")
    private String currency;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
