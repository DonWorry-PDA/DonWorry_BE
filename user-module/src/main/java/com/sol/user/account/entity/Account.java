package com.sol.user.account.entity;

import com.sol.user.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "계좌")
@Getter
@NoArgsConstructor
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "account_id")
    private Long accountId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "계좌유형", length = 20)
    private String accountType;

    @Column(name = "기관명", length = 50)
    private String institutionName;

    @Column(name = "계좌번호", length = 30)
    private String accountNumber;

    @Column(name = "예수금", precision = 15, scale = 0)
    private BigDecimal depositBalance;

    @Column(name = "기존계좌여부")
    private Boolean existingAccount;
}
