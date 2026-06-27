package com.sol.user.monthlysalary.service;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.user.account.entity.Account;
import com.sol.user.account.repository.AccountRepository;
import com.sol.user.holding.dto.HoldingWithProduct;
import com.sol.user.holding.repository.HoldingRepository;
import com.sol.user.monthlysalary.dto.AssetGroupDto;
import com.sol.user.monthlysalary.dto.AssetItemDto;
import com.sol.user.monthlysalary.dto.SalaryAssetExclusionRequest;
import com.sol.user.monthlysalary.dto.SalaryAssetListResponse;
import com.sol.user.monthlysalary.entity.SalaryAssetExclusion;
import com.sol.user.monthlysalary.mapper.SalaryAssetMapper;
import com.sol.user.monthlysalary.repository.SalaryAssetExclusionRepository;
import com.sol.user.portfolio.infra.rest.ProductBatchClient;
import com.sol.user.portfolio.infra.rest.ProductBatchItem;
import com.sol.user.user.entity.User;
import com.sol.user.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SalaryAssetService {

    private static final String DON_WORRY_TYPE = "DON_WORRY";
    private static final String STOCK_PRODUCT_TYPE = "STOCK";

    private final AccountRepository accountRepository;
    private final HoldingRepository holdingRepository;
    private final SalaryAssetExclusionRepository exclusionRepository;
    private final UserRepository userRepository;
    private final SalaryAssetMapper salaryAssetMapper;
    private final ProductBatchClient productBatchClient;

    @Transactional(readOnly = true)
    public SalaryAssetListResponse getAssets(Long userId) {
        validateUserExists(userId);

        Set<String> excludedKeys = exclusionRepository.findAssetKeysByUserId(userId);

        List<AssetGroupDto> assetGroups = buildAssetGroups(userId, excludedKeys);

        return SalaryAssetListResponse.builder()
                .assetGroups(assetGroups)
                .build();
    }

    @Transactional
    public void saveExclusions(Long userId, SalaryAssetExclusionRequest request) {
        User user = getUser(userId);

        List<String> requestedAssetKeys = extractAssetKeys(request);

        validateRequestedAssetKeys(userId, requestedAssetKeys);

        exclusionRepository.deleteByUserUserId(userId);

        if (requestedAssetKeys.isEmpty()) {
            return;
        }

        List<SalaryAssetExclusion> exclusions = requestedAssetKeys.stream()
                .map(assetKey -> SalaryAssetExclusion.builder()
                        .user(user)
                        .assetKey(assetKey)
                        .build())
                .toList();

        exclusionRepository.saveAll(exclusions);
    }

    private List<AssetGroupDto> buildAssetGroups(Long userId, Set<String> excludedKeys) {
        Map<String, List<AssetItemDto>> itemsByType = new LinkedHashMap<>();

        // 계좌 (DON_WORRY 제외) → accountType별 그루핑
        accountRepository.findByUserUserIdAndAccountTypeNot(userId, DON_WORRY_TYPE)
                .forEach(account -> {
                    String type = account.getAccountType();
                    itemsByType.computeIfAbsent(type, k -> new ArrayList<>())
                            .add(salaryAssetMapper.toAccountItem(account, excludedKeys));
                });

        // 보유 종목 (STOCK 제외) → account의 accountType별 그루핑
        List<HoldingWithProduct> allHoldings = holdingRepository.findHoldingsWithAccountTypeByUserId(userId);

        if (!allHoldings.isEmpty()) {
            Map<Long, ProductBatchItem> productMap = productBatchClient.fetchProducts(
                    allHoldings.stream().map(HoldingWithProduct::getProductId).toList()
            );

            allHoldings.stream()
                    .filter(h -> !isStockProduct(productMap.get(h.getProductId())))
                    .forEach(holding -> {
                        String type = holding.getAccountType();
                        itemsByType.computeIfAbsent(type, k -> new ArrayList<>())
                                .add(salaryAssetMapper.toHoldingItem(
                                        holding, productMap.get(holding.getProductId()), excludedKeys));
                    });
        }

        return itemsByType.entrySet().stream()
                .map(entry -> AssetGroupDto.builder()
                        .category(entry.getKey())
                        .categoryLabel(salaryAssetMapper.resolveAccountTypeLabel(entry.getKey()))
                        .items(entry.getValue())
                        .build())
                .toList();
    }

    private boolean isStockProduct(ProductBatchItem product) {
        return product != null && STOCK_PRODUCT_TYPE.equals(product.productType());
    }

    private void validateRequestedAssetKeys(Long userId, List<String> requestedAssetKeys) {
        if (requestedAssetKeys.isEmpty()) {
            return;
        }

        Set<String> availableAssetKeys = getAvailableAssetKeys(userId);

        boolean hasInvalidAssetKey = requestedAssetKeys.stream()
                .anyMatch(assetKey -> !availableAssetKeys.contains(assetKey));

        if (hasInvalidAssetKey) {
            throw new BaseException(ErrorCode.INVALID_INPUT);
        }
    }

    private Set<String> getAvailableAssetKeys(Long userId) {
        Set<String> availableAssetKeys = new HashSet<>();

        accountRepository.findByUserUserIdAndAccountTypeNot(userId, DON_WORRY_TYPE)
                .forEach(account -> availableAssetKeys.add(
                        salaryAssetMapper.createAccountAssetKey(account.getAccountId())));

        List<HoldingWithProduct> allHoldings = holdingRepository.findHoldingsWithAccountTypeByUserId(userId);

        if (!allHoldings.isEmpty()) {
            Map<Long, ProductBatchItem> productMap = productBatchClient.fetchProducts(
                    allHoldings.stream().map(HoldingWithProduct::getProductId).toList()
            );
            allHoldings.stream()
                    .filter(h -> !isStockProduct(productMap.get(h.getProductId())))
                    .forEach(h -> availableAssetKeys.add(
                            salaryAssetMapper.createHoldingAssetKey(h.getProductId())));
        }

        return availableAssetKeys;
    }

    private List<String> extractAssetKeys(SalaryAssetExclusionRequest request) {
        if (request == null || request.getExcludedAssetKeys() == null) {
            return List.of();
        }

        return request.getExcludedAssetKeys()
                .stream()
                .filter(assetKey -> assetKey != null && !assetKey.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));
    }

    private void validateUserExists(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new BaseException(ErrorCode.USER_NOT_FOUND);
        }
    }
}
