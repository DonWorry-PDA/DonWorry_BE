package com.sol.user.asset.service;

import com.sol.user.account.entity.Account;
import com.sol.user.asset.dto.AssetAllocationItem;
import com.sol.user.asset.dto.AssetBreakdown;
import com.sol.user.asset.dto.AssetCompositionResponse;
import com.sol.user.asset.dto.AssetCompositionResponse.AssetGroupItem;
import com.sol.user.asset.infra.rest.DepositDetailClient;
import com.sol.user.asset.infra.rest.DepositDetailItem;
import com.sol.user.asset.mapper.AssetMapper;
import com.sol.user.asset.type.AssetCategory;
import com.sol.user.debt.repository.DebtRepository;
import com.sol.user.holding.dto.HoldingWithProduct;
import com.sol.user.portfolio.infra.rest.ProductBatchItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AssetCompositionService {

    private final AssetAggregator assetAggregator;
    private final DepositDetailClient depositDetailClient;
    private final DebtRepository debtRepository;
    private final AssetMapper assetMapper;

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
                AssetCategory.PENSION, AssetCategory.CMA, AssetCategory.DEPOSIT, AssetCategory.ETC}) {
            if (accountsByCategory.containsKey(cat)) {
                groups.add(assetMapper.toGroupItem(cat, accountsByCategory.get(cat),
                        holdingsByAccountId, snapshot.products(), depositDetails));
            }
        }

        // BROKERAGE(ETF) 계좌: ETF 그룹(비STOCK)과 STOCK 그룹으로 분리
        List<Account> brokerageAccounts = accountsByCategory.getOrDefault(AssetCategory.ETF, List.of());
        if (!brokerageAccounts.isEmpty()) {
            AssetGroupItem etfGroup = assetMapper.toBrokerageGroupItem(
                    AssetCategory.ETF, brokerageAccounts, holdingsByAccountId, snapshot.products(), false);
            AssetGroupItem stockGroup = assetMapper.toBrokerageGroupItem(
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
