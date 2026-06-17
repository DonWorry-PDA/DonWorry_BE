package com.sol.user.liquidityevent.entity;

import com.sol.user.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "liquidity_event")
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

    @Column(name = "timing", length = 20)
    private String timing;

    @Column(name = "required_amount", precision = 15, scale = 0)
    private BigDecimal requiredAmount;

    @Column(name = "reason", length = 100)
    private String reason;
}
