package com.sol.user.pension.entity;

import com.sol.user.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "pension")
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

    @Column(name = "pension_type", length = 20)
    private String pensionType;

    @Column(name = "expected_monthly_amount", precision = 15, scale = 0)
    private BigDecimal expectedMonthlyAmount;

    @Column(name = "variable")
    private Boolean variable;

    @Column(name = "start_age")
    private Integer startAge;
}
