package com.sol.product.assetclass.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "asset_class")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AssetClass {

    @Id
    @Column(name = "asset_class_id")
    private Integer assetClassId;

    @Column(name = "asset_class_name", nullable = false, length = 50)
    private String assetClassName;

    @Column(name = "risk_level", nullable = false)
    private Integer riskLevel;

    @Column(name = "display_order")
    private Integer displayOrder;
}
