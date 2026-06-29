package com.sol.user.trade.entity;

import com.sol.user.account.entity.Account;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Entity
@Table(name = "trade_history")
@Getter
@NoArgsConstructor
public class TradeHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Long orderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "trade_type", length = 10)
    private String tradeType;

    @Column(name = "traded_at")
    private LocalDateTime tradedAt;

    @Column(name = "quantity", precision = 18, scale = 4)
    private BigDecimal quantity;

    @Column(name = "avg_price", precision = 15, scale = 2)
    private BigDecimal avgPrice;

    @Column(name = "trade_amount", precision = 15, scale = 2)
    private BigDecimal tradeAmount;

    @Column(name = "fee", precision = 10, scale = 2)
    private BigDecimal fee;

    @Column(name = "product_id")
    private Long productId;


    public static TradeHistory ofBuy(Account account, Long productId,
                                     BigDecimal quantity, BigDecimal executedPrice) {
        TradeHistory t = new TradeHistory();
        t.account = account;
        t.productId = productId;
        t.tradeType = "BUY";
        t.tradedAt = LocalDateTime.now();
        t.quantity = quantity;
        t.avgPrice = executedPrice;
        t.tradeAmount = quantity.multiply(executedPrice).setScale(0, RoundingMode.HALF_UP);
        t.fee = BigDecimal.ZERO;
        return t;
    }

    /**
     * 목업 페르소나용 거래내역. {@link #ofBuy}와 달리 거래 유형·체결시각을 명시해
     * 과거 매수/매도 내역을 임의 시점으로 시드할 수 있다(마이데이터 거래내역 조회 시연용).
     */
    public static TradeHistory ofMock(Account account, Long productId, String tradeType,
                                      BigDecimal quantity, BigDecimal avgPrice, LocalDateTime tradedAt) {
        TradeHistory t = new TradeHistory();
        t.account = account;
        t.productId = productId;
        t.tradeType = tradeType;
        t.tradedAt = tradedAt;
        t.quantity = quantity;
        t.avgPrice = avgPrice;
        t.tradeAmount = quantity.multiply(avgPrice).setScale(0, RoundingMode.HALF_UP);
        t.fee = BigDecimal.ZERO;
        return t;
    }
}
