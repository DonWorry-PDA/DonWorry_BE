package com.sol.user.report.entity;

import com.sol.user.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "monthly_report")
@Getter
@NoArgsConstructor
public class MonthlyReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_id")
    private Long reportId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "current_month", length = 7)
    private String currentMonth;

    @Column(name = "total_asset", precision = 18, scale = 2)
    private BigDecimal totalAsset;

    @Column(name = "asset_change_amount", precision = 18, scale = 2)
    private BigDecimal assetChangeAmount;

    @Column(name = "monthly_total_income", precision = 18, scale = 2)
    private BigDecimal monthlyTotalIncome;

    @Column(name = "monthly_total_expense", precision = 18, scale = 2)
    private BigDecimal monthlyTotalExpense;

    @Column(name = "stability_change_rate", precision = 5, scale = 2)
    private BigDecimal stabilityChangeRate;

    @Column(name = "current_month_stability_score", precision = 5, scale = 2)
    private BigDecimal currentMonthStabilityScore;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public static MonthlyReport snapshot(User user, String month, BigDecimal totalAsset) {
        MonthlyReport r = new MonthlyReport();
        r.user = user;
        r.currentMonth = month;
        r.totalAsset = totalAsset;
        r.createdAt = LocalDateTime.now();
        return r;
    }

    public void updateTotalAsset(BigDecimal totalAsset) {
        this.totalAsset = totalAsset;
    }
}
