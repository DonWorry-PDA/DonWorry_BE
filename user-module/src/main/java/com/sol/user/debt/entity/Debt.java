package com.sol.user.debt.entity;

import com.sol.user.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "debt")
@Getter
@NoArgsConstructor
public class Debt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "debt_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "institution_name", nullable = false, length = 50)
    private String institutionName;

    @Column(name = "loan_type", nullable = false, length = 30)
    private String loanType;

    @Column(name = "balance", nullable = false, precision = 15, scale = 0)
    private BigDecimal balance;

    @Column(name = "monthly_repayment", nullable = false, precision = 15, scale = 0)
    private BigDecimal monthlyRepayment;

    @Column(name = "interest_rate", precision = 5, scale = 2)
    private BigDecimal interestRate;

    @Column(name = "maturity_date")
    private LocalDate maturityDate;

    public Debt(User user, String institutionName, String loanType, BigDecimal balance,
                BigDecimal monthlyRepayment, BigDecimal interestRate, LocalDate maturityDate) {
        this.user = user;
        this.institutionName = institutionName;
        this.loanType = loanType;
        this.balance = balance;
        this.monthlyRepayment = monthlyRepayment;
        this.interestRate = interestRate;
        this.maturityDate = maturityDate;
    }
}
