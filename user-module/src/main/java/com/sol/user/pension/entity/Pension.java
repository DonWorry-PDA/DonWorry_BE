package com.sol.user.pension.entity;

import com.sol.user.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "연금")
@Getter
@NoArgsConstructor
public class Pension {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pension_id")
    private Long pensionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "퇴직연금유형", length = 20)
    private String pensionType;

    @Column(name = "예상월수령액", precision = 15, scale = 0)
    private BigDecimal expectedMonthlyAmount;

    @Column(name = "변동여부")
    private Boolean variable;

    @Column(name = "수령시작나이")
    private Integer startAge;
}
