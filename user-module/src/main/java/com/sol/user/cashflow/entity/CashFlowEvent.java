package com.sol.user.cashflow.entity;

import com.sol.user.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "cash_flow_event")
@Getter
@NoArgsConstructor
public class CashFlowEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Long eventId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "event_date")
    private LocalDate eventDate;

    @Column(name = "event_type", length = 20)
    private String eventType;

    @Column(name = "amount", precision = 15, scale = 0)
    private BigDecimal amount;

    @Column(name = "flow_type", length = 10)
    private String flowType;

    @Column(name = "status", length = 10)
    private String status;
}
