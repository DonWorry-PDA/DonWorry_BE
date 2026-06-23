package com.sol.user.monthlysalary.service;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
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

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SalaryAssetService {
    private static final List<String> PENSION_TYPES = List.of("IRP", "PENSION_SAVING", "RETIREMENT_PENSION", "PERSONAL_PENSION");
    private static final List<String> DEPOSIT_TYPES = List.of("DEPOSIT", "DEPOSIT_SAVING");
    private static final List<String> INVESTMENT_TYPES = List.of("BROKERAGE", "STOCK_ETF_FUND");

    private static final String CATEGORY_PENSION = "PENSION";
    private static final String CATEGORY_DEPOSIT = "DEPOSIT";
    private static final String CATEGORY_INVESTMENT = "INVESTMENT";

    private static final String CATEGORY_LABEL_PENSION = "연금";
    private static final String CATEGORY_LABEL_DEPOSIT = "예금";
    private static final String CATEGORY_LABEL_INVESTMENT = "투자";

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
        return List.of(
                buildPensionGroup(userId, excludedKeys),
                buildDepositGroup(userId, excludedKeys),
                buildInvestmentGroup(userId, excludedKeys)
        );
    }

    private AssetGroupDto buildPensionGroup(Long userId, Set<String> excludedKeys) {
        List<AssetItemDto> items =
                accountRepository.findByUserUserIdAndAccountTypeIn(userId, PENSION_TYPES)
                        .stream()
                        .map(account -> salaryAssetMapper.toAccountItem(account, excludedKeys))
                        .toList();

        return AssetGroupDto.builder()
                .category(CATEGORY_PENSION)
                .categoryLabel(CATEGORY_LABEL_PENSION)
                .items(items)
                .build();
    }

    private AssetGroupDto buildDepositGroup(Long userId, Set<String> excludedKeys) {
        List<AssetItemDto> items =
                accountRepository.findByUserUserIdAndAccountTypeIn(userId, DEPOSIT_TYPES)
                        .stream()
                        .map(account -> salaryAssetMapper.toAccountItem(account, excludedKeys))
                        .toList();

        return AssetGroupDto.builder()
                .category(CATEGORY_DEPOSIT)
                .categoryLabel(CATEGORY_LABEL_DEPOSIT)
                .items(items)
                .build();
    }

    private AssetGroupDto buildInvestmentGroup(Long userId, Set<String> excludedKeys) {
        List<HoldingWithProduct> holdings = holdingRepository.findByUserIdAndAccountTypes(userId, INVESTMENT_TYPES);

        Map<Long, ProductBatchItem> productMap = productBatchClient.fetchProducts(
                holdings.stream().map(HoldingWithProduct::getProductId).toList()
        );

        List<AssetItemDto> items = holdings.stream()
                .map(holding -> salaryAssetMapper.toHoldingItem(
                        holding, productMap.get(holding.getProductId()), excludedKeys))
                .toList();

        return AssetGroupDto.builder()
                .category(CATEGORY_INVESTMENT)
                .categoryLabel(CATEGORY_LABEL_INVESTMENT)
                .items(items)
                .build();
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

        accountRepository.findByUserUserIdAndAccountTypeIn(userId, PENSION_TYPES)
                .forEach(account -> availableAssetKeys.add(salaryAssetMapper.createAccountAssetKey(account.getAccountId())));

        accountRepository.findByUserUserIdAndAccountTypeIn(userId, DEPOSIT_TYPES)
                .forEach(account -> availableAssetKeys.add(salaryAssetMapper.createAccountAssetKey(account.getAccountId())));

        holdingRepository.findByUserIdAndAccountTypes(userId, INVESTMENT_TYPES)
                .forEach(holding -> availableAssetKeys.add(salaryAssetMapper.createHoldingAssetKey(holding.getHoldingId())));

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
