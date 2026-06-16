package com.sol.user.liquidityevent.entity;

import com.sol.user.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "목돈계획")
@Getter
@NoArgsConstructor
public class LiquidityEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "liquidity_event_id")
    private Long liquidityEventId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "시점", length = 20)
    private String timing;

    @Column(name = "필요금액", precision = 15, scale = 0)
    private BigDecimal requiredAmount;

    @Column(name = "사유", length = 100)
    private String reason;
}
