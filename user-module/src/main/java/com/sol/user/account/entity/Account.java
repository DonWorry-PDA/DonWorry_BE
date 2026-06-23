package com.sol.user.account.entity;

import com.sol.user.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "account")
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

    @Column(name = "account_type", length = 20)
    private String accountType;

    @Column(name = "institution_name", length = 50)
    private String institutionName;

    @Column(name = "account_number", length = 30)
    private String accountNumber;

    @Column(name = "deposit_balance", precision = 15, scale = 0)
    private BigDecimal depositBalance;

    @Column(name = "existing_account")
    private Boolean existingAccount;

    @Column(name = "opened_at")
    private LocalDate openedAt;

    public static Account createDonWorry(User user, String accountNumber, LocalDate openedAt) {
        Account account = new Account();
        account.user = user;
        account.accountType = "DON_WORRY";
        account.institutionName = "신한은행";
        account.accountNumber = accountNumber;
        account.depositBalance = BigDecimal.ZERO;
        account.existingAccount = false;
        account.openedAt = openedAt;
        return account;
    }

    public Account(User user, String accountType, String institutionName,
                   String accountNumber, BigDecimal depositBalance, Boolean existingAccount) {
        this.user = user;
        this.accountType = accountType;
        this.institutionName = institutionName;
        this.accountNumber = accountNumber;
        this.depositBalance = depositBalance;
        this.existingAccount = existingAccount;
    }

    public void updateMock(String institutionName, String accountNumber, BigDecimal depositBalance) {
        this.institutionName = institutionName;
        this.accountNumber = accountNumber;
        this.depositBalance = depositBalance;
        this.existingAccount = true;
    }
}
