package com.sol.user.holding.entity;

import com.sol.user.account.entity.Account;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "holding")
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

    @Column(name = "evaluation_amount", precision = 15, scale = 0)
    private BigDecimal evaluationAmount;

    @Column(name = "quantity", precision = 18, scale = 4)
    private BigDecimal quantity;

    @Column(name = "avg_purchase_price", length = 255)
    private String avgPurchasePrice;

    @Column(name = "unrealized_gain_loss", length = 255)
    private String unrealizedGainLoss;

    @Column(name = "frozen")
    private Boolean frozen;

    public Holding(Account account, Long productId, BigDecimal evaluationAmount) {
        this.account = account;
        this.productId = productId;
        this.evaluationAmount = evaluationAmount;
    }
}
