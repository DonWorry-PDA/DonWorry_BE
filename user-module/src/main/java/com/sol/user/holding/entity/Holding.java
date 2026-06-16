package com.sol.user.holding.entity;

import com.sol.user.account.entity.Account;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "보유자산")
@Getter
@NoArgsConstructor
public class Holding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "holding_id")
    private Long holdingId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "평가금액", precision = 15, scale = 0)
    private BigDecimal evaluationAmount;

    @Column(name = "보유수량", precision = 18, scale = 4)
    private BigDecimal quantity;

    @Column(name = "평균매입단가", length = 255)
    private String avgPurchasePrice;

    @Column(name = "평가손익", length = 255)
    private String unrealizedGainLoss;

    @Column(name = "동결여부")
    private Boolean frozen;
}
