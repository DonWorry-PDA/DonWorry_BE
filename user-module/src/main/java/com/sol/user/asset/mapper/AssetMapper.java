package com.sol.user.asset.mapper;

import com.sol.user.account.entity.Account;
import com.sol.user.asset.dto.AssetAllocationItem;
import com.sol.user.asset.dto.AssetBreakdown;
import com.sol.user.asset.dto.AssetBreakdown.AssetRole;
import com.sol.user.asset.dto.AssetCompositionResponse.AssetAccountItem;
import com.sol.user.asset.dto.AssetCompositionResponse.AssetGroupItem;
import com.sol.user.asset.dto.AssetCompositionResponse.AssetHoldingItem;
import com.sol.user.asset.dto.AssetHubResponse;
import com.sol.user.asset.dto.AssetIncomeResponse.IncomeSource;
import com.sol.user.asset.dto.InvestmentCheckResponse.RoleContribution;
import com.sol.user.asset.infra.rest.DepositDetailItem;
import com.sol.user.asset.service.AssetAggregator;
import com.sol.user.asset.type.AssetCategory;
import com.sol.user.holding.dto.HoldingWithProduct;
import com.sol.user.portfolio.infra.rest.ProductBatchItem;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AssetMapper {

    // ─────────────────────────────────────────────────────────────────
    // Hub
    // ─────────────────────────────────────────────────────────────────

    /**
     * 카테고리별 금액을 정수 % 로 변환.
     * 내림 후 잔여 %를 소수부가 큰 항목부터 1씩 배분해 합이 정확히 100이 되도록 보정.
     * 표시 순서는 enum 선언 순서(연금·예금·ETF·주식·기타) 고정.
     */
    public List<AssetAllocationItem> toCategoryAllocation(Map<AssetCategory, BigDecimal> byCategory, BigDecimal total) {
        if (total.signum() <= 0) {
            return List.of();
        }

        List<Bucket> buckets = new ArrayList<>();
        for (Map.Entry<AssetCategory, BigDecimal> entry : byCategory.entrySet()) {
            BigDecimal pct = entry.getValue()
                    .multiply(BigDecimal.valueOf(100))
                    .divide(total, 4, RoundingMode.HALF_UP);
            int floor = pct.setScale(0, RoundingMode.DOWN).intValue();
            BigDecimal remainder = pct.subtract(BigDecimal.valueOf(floor));
            buckets.add(new Bucket(entry.getKey(), floor, remainder));
        }

        int assigned = buckets.stream().mapToInt(Bucket::ratio).sum();
        int leftover = 100 - assigned;
        buckets.stream()
                .sorted(Comparator.comparing(Bucket::remainder).reversed())
                .limit(Math.max(leftover, 0))
                .forEach(Bucket::increment);

        return buckets.stream()
                .sorted(Comparator.comparing(b -> b.category.ordinal()))
                .map(b -> new AssetAllocationItem(b.category.getLabel(), b.ratio()))
                .toList();
    }

    /**
     * ETF holding을 한 번 순회해 ticker별 수량 목록과 DB 평가액 합계를 동시에 산출.
     * 동일 ETF를 여러 계좌에 나눠 보유한 경우 ticker 기준 수량 합산.
     * 개별주식(productType=STOCK)은 {@link #toStockSnapshot}에서 따로 집계하므로 제외한다.
     */
    public EtfSnapshot toEtfSnapshot(AssetAggregator.AssetSnapshot snapshot) {
        Map<Long, ProductBatchItem> products = snapshot.products();
        Map<String, BigDecimal> quantityByTicker = new LinkedHashMap<>();
        BigDecimal snapshotAmount = BigDecimal.ZERO;

        for (HoldingWithProduct h : snapshot.holdings()) {
            ProductBatchItem p = products.get(h.getProductId());
            if (p == null || p.tickerCode() == null) continue;
            if ("STOCK".equals(p.productType())) continue;
            quantityByTicker.merge(p.tickerCode(), nz(h.getQuantity()), BigDecimal::add);
            snapshotAmount = snapshotAmount.add(nz(h.getEvaluationAmount()));
        }

        List<AssetHubResponse.EtfHoldingItem> holdings = quantityByTicker.entrySet().stream()
                .map(e -> new AssetHubResponse.EtfHoldingItem(e.getKey(), e.getValue()))
                .toList();
        return new EtfSnapshot(holdings, snapshotAmount);
    }

    public record EtfSnapshot(List<AssetHubResponse.EtfHoldingItem> holdings, BigDecimal snapshotAmount) {}

    /**
     * 개별주식(productType=STOCK) holding을 한 번 순회해 ticker별 수량 목록과 DB 평가액 합계를 산출.
     * 동일 종목을 여러 계좌에 나눠 보유한 경우 ticker 기준 수량 합산. ({@link #toEtfSnapshot}과 대칭)
     */
    public StockSnapshot toStockSnapshot(AssetAggregator.AssetSnapshot snapshot) {
        Map<Long, ProductBatchItem> products = snapshot.products();
        Map<String, BigDecimal> quantityByTicker = new LinkedHashMap<>();
        BigDecimal snapshotAmount = BigDecimal.ZERO;

        for (HoldingWithProduct h : snapshot.holdings()) {
            ProductBatchItem p = products.get(h.getProductId());
            if (p == null || p.tickerCode() == null) continue;
            if (!"STOCK".equals(p.productType())) continue;
            quantityByTicker.merge(p.tickerCode(), nz(h.getQuantity()), BigDecimal::add);
            snapshotAmount = snapshotAmount.add(nz(h.getEvaluationAmount()));
        }

        List<AssetHubResponse.StockHoldingItem> holdings = quantityByTicker.entrySet().stream()
                .map(e -> new AssetHubResponse.StockHoldingItem(e.getKey(), e.getValue()))
                .toList();
        return new StockSnapshot(holdings, snapshotAmount);
    }

    public record StockSnapshot(List<AssetHubResponse.StockHoldingItem> holdings, BigDecimal snapshotAmount) {}

    // ─────────────────────────────────────────────────────────────────
    // Composition
    // ─────────────────────────────────────────────────────────────────

    public AssetHoldingItem toHoldingItem(HoldingWithProduct holding, ProductBatchItem product) {
        return AssetHoldingItem.builder()
                .productName(product == null ? "알 수 없음" : product.productName())
                .tickerCode(product == null ? null : product.tickerCode())
                .quantity(holding.getQuantity())
                .evaluationAmount(nz(holding.getEvaluationAmount()))
                .build();
    }

    public AssetAccountItem toAccountItem(Account account,
                                          Map<Long, List<HoldingWithProduct>> holdingsByAccountId,
                                          Map<Long, ProductBatchItem> products,
                                          Map<Long, DepositDetailItem> depositDetails) {
        BigDecimal interestRate = null;
        LocalDate maturityDate = null;
        boolean isDeposit = "DEPOSIT".equals(account.getAccountType()) && account.getProductId() != null;
        DepositDetailItem depositDetail = isDeposit ? depositDetails.get(account.getProductId()) : null;

        if (depositDetail != null) {
            interestRate = depositDetail.interestRate();
            if (account.getOpenedAt() != null && depositDetail.maturityMonths() != null) {
                maturityDate = account.getOpenedAt().plusMonths(depositDetail.maturityMonths());
            }
        }

        List<AssetHoldingItem> holdingItems;
        BigDecimal balance;

        if (depositDetail != null) {
            // DEPOSIT: 예금 상품을 가상 holding으로 표시, balance=0으로 이중 합산 방지
            String name = depositDetail.productName() != null ? depositDetail.productName() : account.getInstitutionName();
            holdingItems = List.of(AssetHoldingItem.builder()
                    .productName(name)
                    .tickerCode(null)
                    .quantity(null)
                    .evaluationAmount(nz(account.getDepositBalance()))
                    .build());
            balance = BigDecimal.ZERO;
        } else {
            holdingItems = holdingsByAccountId
                    .getOrDefault(account.getAccountId(), List.of()).stream()
                    .map(h -> toHoldingItem(h, products.get(h.getProductId())))
                    .toList();
            balance = nz(account.getDepositBalance());
        }

        return AssetAccountItem.builder()
                .accountId(account.getAccountId())
                .institutionName(account.getInstitutionName())
                .accountType(account.getAccountType())
                .balance(balance)
                .interestRate(interestRate)
                .maturityDate(maturityDate)
                .holdings(holdingItems)
                .build();
    }

    public AssetGroupItem toGroupItem(AssetCategory category,
                                      List<Account> accounts,
                                      Map<Long, List<HoldingWithProduct>> holdingsByAccountId,
                                      Map<Long, ProductBatchItem> products,
                                      Map<Long, DepositDetailItem> depositDetails) {
        List<AssetAccountItem> accountItems = accounts.stream()
                .map(a -> toAccountItem(a, holdingsByAccountId, products, depositDetails))
                .toList();
        BigDecimal totalAmount = accountItems.stream()
                .map(a -> {
                    BigDecimal balance = nz(a.getBalance());
                    BigDecimal holdingsSum = a.getHoldings().stream()
                            .map(h -> nz(h.getEvaluationAmount()))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return balance.add(holdingsSum);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return AssetGroupItem.builder()
                .category(category.name())
                .label(category.getLabel())
                .totalAmount(totalAmount)
                .accounts(accountItems)
                .build();
    }

    /**
     * BROKERAGE 계좌를 ETF(비STOCK)와 STOCK 두 그룹으로 분리.
     * stockOnly=false → 계좌 잔액 + 비STOCK 보유종목 (ETF 그룹)
     * stockOnly=true  → STOCK 보유종목만, 잔액 없음 (STOCK 그룹)
     */
    public AssetGroupItem toBrokerageGroupItem(AssetCategory category,
                                               List<Account> accounts,
                                               Map<Long, List<HoldingWithProduct>> holdingsByAccountId,
                                               Map<Long, ProductBatchItem> products,
                                               boolean stockOnly) {
        List<AssetAccountItem> accountItems = new ArrayList<>();
        for (Account account : accounts) {
            BigDecimal balance = stockOnly ? BigDecimal.ZERO : nz(account.getDepositBalance());
            List<AssetHoldingItem> holdingItems = holdingsByAccountId
                    .getOrDefault(account.getAccountId(), List.of()).stream()
                    .filter(h -> {
                        ProductBatchItem p = products.get(h.getProductId());
                        if (p == null) return false;
                        boolean isStock = "STOCK".equals(p.productType());
                        return stockOnly ? isStock : !isStock;
                    })
                    .map(h -> toHoldingItem(h, products.get(h.getProductId())))
                    .toList();
            if (balance.signum() == 0 && holdingItems.isEmpty()) continue;
            accountItems.add(AssetAccountItem.builder()
                    .accountId(account.getAccountId())
                    .institutionName(account.getInstitutionName())
                    .accountType(account.getAccountType())
                    .balance(balance)
                    .holdings(holdingItems)
                    .build());
        }
        BigDecimal totalAmount = accountItems.stream()
                .map(a -> {
                    BigDecimal b = nz(a.getBalance());
                    BigDecimal h = a.getHoldings().stream()
                            .map(hi -> nz(hi.getEvaluationAmount()))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return b.add(h);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return AssetGroupItem.builder()
                .category(category.name())
                .label(category.getLabel())
                .totalAmount(totalAmount)
                .accounts(accountItems)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────
    // Income
    // ─────────────────────────────────────────────────────────────────

    public List<IncomeSource> toIncomeSources(BigDecimal nationalPension, BigDecimal etfDividend,
                                               BigDecimal depositInterest, BigDecimal pensionDividend) {
        List<IncomeSource> sources = new ArrayList<>();
        if (nationalPension.signum() > 0) {
            sources.add(IncomeSource.builder()
                    .type("NATIONAL_PENSION").label("국민연금")
                    .amount(nationalPension).locked(false).build());
        }
        if (etfDividend.signum() > 0) {
            sources.add(IncomeSource.builder()
                    .type("ETF_DIVIDEND").label("ETF 배당")
                    .amount(etfDividend).locked(false).build());
        }
        if (depositInterest.signum() > 0) {
            sources.add(IncomeSource.builder()
                    .type("DEPOSIT_INTEREST").label("예금 이자")
                    .amount(depositInterest).locked(false).build());
        }
        if (pensionDividend.signum() > 0) {
            sources.add(IncomeSource.builder()
                    .type("PENSION_DIVIDEND").label("연금 계좌 ETF 배당")
                    .amount(pensionDividend).locked(true).build());
        }
        return sources;
    }

    // ─────────────────────────────────────────────────────────────────
    // InvestmentCheck
    // ─────────────────────────────────────────────────────────────────

    public List<RoleContribution> toRoleContributions(AssetBreakdown breakdown,
                                                       BigDecimal cashflowMonthly, BigDecimal stockMonthly) {
        return breakdown.roleAllocation().stream()
                .map(slice -> RoleContribution.builder()
                        .role(slice.role().name())
                        .label(roleLabel(slice.role()))
                        .amount(slice.amount())
                        .ratio(slice.ratio())
                        .monthlyCashflow(switch (slice.role()) {
                            case CASHFLOW -> cashflowMonthly;
                            case GROWTH -> stockMonthly;
                            case IDLE, PENSION -> BigDecimal.ZERO;
                        })
                        .note(roleNote(slice.role()))
                        .build())
                .toList();
    }

    // ─────────────────────────────────────────────────────────────────
    // Internals
    // ─────────────────────────────────────────────────────────────────

    private String roleLabel(AssetRole role) {
        return switch (role) {
            case CASHFLOW -> "현금흐름";
            case GROWTH -> "성장";
            case IDLE -> "잠자는 돈";
            case PENSION -> "연금";
        };
    }

    private String roleNote(AssetRole role) {
        return switch (role) {
            case CASHFLOW -> "매달 배당·이자가 들어오는 돈";
            case GROWTH -> "자본차익을 노리는 돈 (배당이 나오면 함께 표시돼요)";
            case IDLE -> "아직 일하지 않고 쉬고 있는 현금";
            case PENSION -> "55세까지 묶인 노후 자금";
        };
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static final class Bucket {
        private final AssetCategory category;
        private int ratio;
        private final BigDecimal remainder;

        private Bucket(AssetCategory category, int ratio, BigDecimal remainder) {
            this.category = category;
            this.ratio = ratio;
            this.remainder = remainder;
        }

        private int ratio() { return ratio; }
        private BigDecimal remainder() { return remainder; }
        private void increment() { ratio++; }
    }
}
