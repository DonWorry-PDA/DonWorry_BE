package com.sol.user.trade.entity;

import com.sol.user.account.entity.Account;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "투자거래내역")
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

    @Column(name = "거래 유형", length = 10)
    private String tradeType;

    @Column(name = "거래일시")
    private LocalDateTime tradedAt;

    @Column(name = "거래수량", precision = 18, scale = 4)
    private BigDecimal quantity;

    @Column(name = "평균단가", precision = 15, scale = 2)
    private BigDecimal avgPrice;

    @Column(name = "거래 금액", precision = 15, scale = 2)
    private BigDecimal tradeAmount;

    @Column(name = "수수료", precision = 10, scale = 2)
    private BigDecimal fee;
}
