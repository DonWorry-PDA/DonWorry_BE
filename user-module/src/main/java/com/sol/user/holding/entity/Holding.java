package com.sol.user.holding.entity;

import com.sol.user.account.entity.Account;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Table(name = "holding", uniqueConstraints = {
        @UniqueConstraint(name = "uk_holding_account_product", columnNames = {"account_id", "product_id"})
})
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

    public Holding(Account account, Long productId, BigDecimal evaluationAmount, BigDecimal quantity) {
        this.account = account;
        this.productId = productId;
        this.evaluationAmount = evaluationAmount;
        this.quantity = quantity;
    }

    /** mock 재동기화 시 기존 보유행을 재사용해 평가액·수량만 갱신한다(holdingId 유지 — #207). */
    public void updateMockValuation(BigDecimal evaluationAmount, BigDecimal quantity) {
        this.evaluationAmount = evaluationAmount;
        this.quantity = quantity;
    }

    public void refreshValuation(BigDecimal currentPrice) {
        if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0 || quantity == null) {
            return;
        }
        this.evaluationAmount = quantity.multiply(currentPrice).setScale(0, RoundingMode.HALF_UP);
    }

    public static Holding ofBuy(Account account, Long productId, BigDecimal quantity, BigDecimal currentPrice) {
        Holding h = new Holding();
        h.account = account;
        h.productId = productId;
        h.quantity = quantity;
        h.avgPurchasePrice = currentPrice.toPlainString();
        h.evaluationAmount = quantity.multiply(currentPrice).setScale(0, RoundingMode.HALF_UP);
        h.unrealizedGainLoss = "0";
        h.frozen = false;
        return h;
    }

    public void addPurchase(BigDecimal newQuantity, BigDecimal currentPrice) {
        BigDecimal oldAvg = (avgPurchasePrice == null || avgPurchasePrice.isBlank())
                ? currentPrice : new BigDecimal(avgPurchasePrice);
        BigDecimal totalQty = this.quantity.add(newQuantity);
        BigDecimal newAvg = this.quantity.multiply(oldAvg)
                .add(newQuantity.multiply(currentPrice))
                .divide(totalQty, 2, RoundingMode.HALF_UP);
        this.quantity = totalQty;
        this.avgPurchasePrice = newAvg.toPlainString();
        this.evaluationAmount = totalQty.multiply(currentPrice).setScale(0, RoundingMode.HALF_UP);
        this.unrealizedGainLoss = currentPrice.subtract(newAvg)
                .multiply(totalQty).setScale(0, RoundingMode.HALF_UP).toPlainString();
    }
}
