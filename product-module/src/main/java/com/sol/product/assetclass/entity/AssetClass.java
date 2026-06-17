package com.sol.product.assetclass.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "자산군")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AssetClass {

    @Id
    @Column(name = "asset_class_id")
    private Integer assetClassId;

    @Column(name = "자산군이름", nullable = false, length = 50)
    private String assetClassName;

    @Column(name = "위험도", nullable = false)
    private Integer riskLevel;

    @Column(name = "화면표시순서")
    private Integer displayOrder;
}
