package com.sol.user.asset.service;

import com.sol.user.account.entity.Account;
import com.sol.user.asset.dto.AssetAllocationItem;
import com.sol.user.asset.dto.AssetBreakdown;
import com.sol.user.asset.dto.AssetCompositionResponse;
import com.sol.user.asset.dto.AssetCompositionResponse.AssetAccountItem;
import com.sol.user.asset.dto.AssetCompositionResponse.AssetGroupItem;
import com.sol.user.asset.dto.AssetCompositionResponse.AssetHoldingItem;
import com.sol.user.asset.infra.rest.DepositDetailClient;
import com.sol.user.asset.infra.rest.DepositDetailItem;
import com.sol.user.asset.type.AssetCategory;
import com.sol.user.debt.repository.DebtRepository;
import com.sol.user.holding.dto.HoldingWithProduct;
import com.sol.user.portfolio.infra.rest.ProductBatchItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AssetCompositionService {

    private final AssetAggregator assetAggregator;
    private final DepositDetailClient depositDetailClient;
    private final DebtRepository debtRepository;

    public AssetCompositionResponse getComposition(Long userId) {
        AssetAggregator.AssetSnapshot snapshot = assetAggregator.aggregateSnapshot(userId);
        BigDecimal totalAsset = snapshot.breakdown().grossTotal();
        BigDecimal totalDebt = debtRepository.sumBalanceByUserId(userId);

        // DEPOSIT 계좌 product_id 추출 → 배치 조회
        List<Long> depositProductIds = snapshot.accounts().stream()
                .filter(a -> "DEPOSIT".equals(a.getAccountType()) && a.getProductId() != null)
                .map(Account::getProductId)
                .toList();
        Map<Long, DepositDetailItem> depositDetails = depositDetailClient.fetchDepositDetails(depositProductIds);

        // 보유종목을 accountId 기준으로 그룹
        Map<Long, List<HoldingWithProduct>> holdingsByAccountId = snapshot.holdings().stream()
                .collect(Collectors.groupingBy(HoldingWithProduct::getAccountId));

        // 카테고리별 계좌 그룹
        Map<AssetCategory, List<Account>> accountsByCategory = new EnumMap<>(AssetCategory.class);
        for (Account account : snapshot.accounts()) {
            AssetCategory category = AssetCategory.fromAccountType(account.getAccountType());
            accountsByCategory.computeIfAbsent(category, k -> new ArrayList<>()).add(account);
        }

        // PENSION, DEPOSIT, ETC — 기존 로직 그대로
        List<AssetGroupItem> groups = new ArrayList<>();
        for (AssetCategory cat : new AssetCategory[]{
                AssetCategory.PENSION, AssetCategory.DEPOSIT, AssetCategory.ETC}) {
            if (accountsByCategory.containsKey(cat)) {
                groups.add(buildGroup(cat, accountsByCategory.get(cat),
                        holdingsByAccountId, snapshot.products(), depositDetails));
            }
        }

        // BROKERAGE(ETF) 계좌: ETF 그룹(비STOCK)과 STOCK 그룹으로 분리
        List<Account> brokerageAccounts = accountsByCategory.getOrDefault(AssetCategory.ETF, List.of());
        if (!brokerageAccounts.isEmpty()) {
            AssetGroupItem etfGroup = buildBrokerageGroup(
                    AssetCategory.ETF, brokerageAccounts, holdingsByAccountId, snapshot.products(), false);
            AssetGroupItem stockGroup = buildBrokerageGroup(
                    AssetCategory.STOCK, brokerageAccounts, holdingsByAccountId, snapshot.products(), true);
            if (etfGroup.getTotalAmount().signum() > 0) groups.add(etfGroup);
            if (stockGroup.getTotalAmount().signum() > 0) groups.add(stockGroup);
        }

        return AssetCompositionResponse.builder()
                .totalAsset(totalAsset)
                .totalDebt(totalDebt)
                .netWorth(totalAsset.subtract(totalDebt))
                .allocation(buildAllocation(snapshot.breakdown(), totalAsset))
                .groups(groups)
                .build();
    }

    private AssetGroupItem buildGroup(AssetCategory category,
                                      List<Account> accounts,
                                      Map<Long, List<HoldingWithProduct>> holdingsByAccountId,
                                      Map<Long, ProductBatchItem> products,
                                      Map<Long, DepositDetailItem> depositDetails) {
        List<AssetAccountItem> accountItems = accounts.stream()
                .map(a -> buildAccountItem(a, holdingsByAccountId, products, depositDetails))
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

    private AssetAccountItem buildAccountItem(Account account,
                                              Map<Long, List<HoldingWithProduct>> holdingsByAccountId,
                                              Map<Long, ProductBatchItem> products,
                                              Map<Long, DepositDetailItem> depositDetails) {
        BigDecimal interestRate = null;
        LocalDate maturityDate = null;
        if ("DEPOSIT".equals(account.getAccountType()) && account.getProductId() != null) {
            DepositDetailItem detail = depositDetails.get(account.getProductId());
            if (detail != null) {
                interestRate = detail.interestRate();
                if (account.getOpenedAt() != null && detail.maturityMonths() != null) {
                    maturityDate = account.getOpenedAt().plusMonths(detail.maturityMonths());
                }
            }
        }
        List<AssetHoldingItem> holdingItems = holdingsByAccountId
                .getOrDefault(account.getAccountId(), List.of()).stream()
                .map(h -> {
                    ProductBatchItem product = products.get(h.getProductId());
                    return AssetHoldingItem.builder()
                            .productName(product == null ? "알 수 없음" : product.productName())
                            .evaluationAmount(nz(h.getEvaluationAmount()))
                            .build();
                })
                .toList();
        return AssetAccountItem.builder()
                .accountId(account.getAccountId())
                .institutionName(account.getInstitutionName())
                .accountType(account.getAccountType())
                .balance(nz(account.getDepositBalance()))
                .interestRate(interestRate)
                .maturityDate(maturityDate)
                .holdings(holdingItems)
                .build();
    }

    /**
     * BROKERAGE 계좌를 ETF(비STOCK)와 STOCK 두 그룹으로 분리한다.
     * stockOnly=false → 계좌 잔액 + 비STOCK 보유종목 (ETF 그룹)
     * stockOnly=true  → STOCK 보유종목만, 잔액 없음 (STOCK 그룹)
     */
    private AssetGroupItem buildBrokerageGroup(AssetCategory category,
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
                        ProductBatchItem product = products.get(h.getProductId());
                        boolean isStock = product != null && "STOCK".equals(product.productType());
                        return stockOnly ? isStock : !isStock;
                    })
                    .map(h -> {
                        ProductBatchItem product = products.get(h.getProductId());
                        return AssetHoldingItem.builder()
                                .productName(product == null ? "알 수 없음" : product.productName())
                                .evaluationAmount(nz(h.getEvaluationAmount()))
                                .build();
                    })
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

    private List<AssetAllocationItem> buildAllocation(AssetBreakdown breakdown, BigDecimal total) {
        if (total.signum() <= 0) {
            return List.of();
        }
        // AssetBreakdown.roleAllocation()을 활용해 역할 기반 배분 생성
        return breakdown.roleAllocation().stream()
                .map(slice -> new AssetAllocationItem(slice.role().getLabel(), slice.ratio()))
                .toList();
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
