package com.sol.user.cashflow.entity;

import com.sol.user.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "현금흐름이벤트")
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

    @Column(name = "이벤트발생날짜")
    private LocalDate eventDate;

    @Column(name = "이벤트유형", length = 20)
    private String eventType;

    @Column(name = "금액", precision = 15, scale = 0)
    private BigDecimal amount;

    @Column(name = "입출금구분", length = 10)
    private String flowType;

    @Column(name = "상태", length = 10)
    private String status;
}
