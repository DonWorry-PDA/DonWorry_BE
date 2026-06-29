package com.sol.user.branch.entity;

import com.sol.common.entity.BaseEntity;
import com.sol.user.branch.type.Institution;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 영업점. 위경도를 보유하며, 사용자 위치 기준 거리 계산의 대상이 된다. */
@Entity
@Table(name = "branch", indexes = @Index(name = "idx_branch_institution", columnList = "institution"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Branch extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "branch_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "institution", length = 30, nullable = false)
    private Institution institution;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "phone", length = 30)
    private String phone;

    /** 광역 지역명(프리미어 데이터의 '지역' 컬럼). 은행 데이터엔 없어 null일 수 있다. */
    @Column(name = "region", length = 50)
    private String region;

    @Column(name = "latitude", nullable = false)
    private double latitude;

    @Column(name = "longitude", nullable = false)
    private double longitude;

    @Builder
    private Branch(Institution institution, String name, String address, String phone,
                   String region, double latitude, double longitude) {
        this.institution = institution;
        this.name = name;
        this.address = address;
        this.phone = phone;
        this.region = region;
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
