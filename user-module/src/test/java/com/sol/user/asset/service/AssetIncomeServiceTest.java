package com.sol.user.asset.service;

import com.sol.user.account.entity.Account;
import com.sol.user.account.repository.AccountRepository;
import com.sol.user.asset.dto.AssetIncomeResponse;
import com.sol.user.asset.dto.AssetIncomeResponse.IncomeSource;
import com.sol.user.asset.infra.rest.DepositDetailClient;
import com.sol.user.asset.infra.rest.DepositDetailItem;
import com.sol.user.holding.dto.HoldingWithQuantityAndType;
import com.sol.user.holding.repository.HoldingRepository;
import com.sol.user.asset.mapper.AssetMapper;
import com.sol.user.pension.repository.PensionRepository;
import com.sol.user.portfolio.infra.rest.ProductBatchClient;
import com.sol.user.user.entity.User;
import com.sol.user.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetIncomeServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private PensionRepository pensionRepository;
    @Mock
    private HoldingRepository holdingRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private ProductBatchClient productBatchClient;
    @Mock
    private DepositDetailClient depositDetailClient;
    @Mock
    private UserRepository userRepository;

    private AssetIncomeService service;

    @BeforeEach
    void setUp() {
        service = new AssetIncomeService(
                pensionRepository,
                holdingRepository,
                accountRepository,
                productBatchClient,
                depositDetailClient,
                userRepository,
                new AssetMapper()
        );
        // 국민연금 수령 게이팅: 수령 중(true)일 때만 income에 국민연금이 포함된다.
        User user = mock(User.class);
        lenient().when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        lenient().when(user.getNationalPensionReceiving()).thenReturn(true);
    }

    @Test
    @DisplayName("4가지 수입원이 모두 있을 때 합산과 분류가 올바르다")
    void getIncome_allSources_calculatedCorrectly() {
        // Arrange
        // 국민연금: 300,000
        given(pensionRepository.findMonthlyAmount(USER_ID, "NATIONAL"))
                .willReturn(Optional.of(new BigDecimal("300000")));

        // 비연금 ETF 보유: productId=100, quantity=100
        HoldingWithQuantityAndType brokerageHolding = mockHolding(100L, new BigDecimal("100"), "BROKERAGE");
        // 연금 ETF 보유: productId=200, quantity=50
        HoldingWithQuantityAndType pensionHolding = mockHolding(200L, new BigDecimal("50"), "IRP");
        given(holdingRepository.findHoldingsWithQuantityAndTypeByUserId(USER_ID))
                .willReturn(List.of(brokerageHolding, pensionHolding));

        // ETF 비연금 배당: 100 * 2000 = 200,000
        given(productBatchClient.fetchEtfMonthlyDividends(List.of(100L)))
                .willReturn(Map.of(100L, new BigDecimal("2000")));
        // ETF 연금 배당: 50 * 5000 = 250,000
        given(productBatchClient.fetchEtfMonthlyDividends(List.of(200L)))
                .willReturn(Map.of(200L, new BigDecimal("5000")));

        // DEPOSIT 계좌: balance=12,000,000, interestRate=1.0 → 12,000,000 * 1.0 / 1200 = 10,000
        Account depositAccount = mockDepositAccount(300L, new BigDecimal("12000000"));
        given(accountRepository.findByUserUserId(USER_ID))
                .willReturn(List.of(depositAccount));
        given(depositDetailClient.fetchDepositDetails(List.of(300L)))
                .willReturn(Map.of(300L, new DepositDetailItem(300L, new BigDecimal("1.0"), 12)));

        // 미실현 손익
        given(holdingRepository.sumUnrealizedGainLossByUserId(USER_ID))
                .willReturn(new BigDecimal("1200000"));

        // Act
        AssetIncomeResponse response = service.getIncome(USER_ID);

        // Assert — 금액
        // accessibleIncome = 300000 + 200000 + 10000 = 510000
        assertThat(response.getAccessibleIncome()).isEqualByComparingTo("510000");
        // lockedIncome = 250000
        assertThat(response.getLockedIncome()).isEqualByComparingTo("250000");
        // totalMonthlyIncome = 510000 + 250000 = 760000
        assertThat(response.getTotalMonthlyIncome()).isEqualByComparingTo("760000");
        assertThat(response.getTotalUnrealizedGainLoss()).isEqualByComparingTo("1200000");

        // Assert — sources 목록 (amount > 0인 항목만)
        assertThat(response.getSources()).hasSize(4);

        IncomeSource nationalPension = findSource(response, "NATIONAL_PENSION");
        assertThat(nationalPension.getAmount()).isEqualByComparingTo("300000");
        assertThat(nationalPension.isLocked()).isFalse();

        IncomeSource etfDividend = findSource(response, "ETF_DIVIDEND");
        assertThat(etfDividend.getAmount()).isEqualByComparingTo("200000");
        assertThat(etfDividend.isLocked()).isFalse();

        IncomeSource depositInterest = findSource(response, "DEPOSIT_INTEREST");
        assertThat(depositInterest.getAmount()).isEqualByComparingTo("10000");
        assertThat(depositInterest.isLocked()).isFalse();

        IncomeSource pensionDividend = findSource(response, "PENSION_DIVIDEND");
        assertThat(pensionDividend.getAmount()).isEqualByComparingTo("250000");
        assertThat(pensionDividend.isLocked()).isTrue();
    }

    @Test
    @DisplayName("금액이 0인 수입원은 sources 목록에 포함되지 않는다")
    void getIncome_zeroAmountSource_excludedFromList() {
        // Arrange — 국민연금 없음, ETF 없음, 예금 없음, 연금ETF 없음
        given(pensionRepository.findMonthlyAmount(USER_ID, "NATIONAL"))
                .willReturn(Optional.empty());
        given(holdingRepository.findHoldingsWithQuantityAndTypeByUserId(USER_ID))
                .willReturn(List.of());
        given(accountRepository.findByUserUserId(USER_ID))
                .willReturn(List.of());
        given(holdingRepository.sumUnrealizedGainLossByUserId(USER_ID))
                .willReturn(BigDecimal.ZERO);

        // Act
        AssetIncomeResponse response = service.getIncome(USER_ID);

        // Assert
        assertThat(response.getSources()).isEmpty();
        assertThat(response.getTotalMonthlyIncome()).isEqualByComparingTo("0");
        assertThat(response.getAccessibleIncome()).isEqualByComparingTo("0");
        assertThat(response.getLockedIncome()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("accessibleIncome과 lockedIncome의 합이 totalMonthlyIncome이다")
    void getIncome_totalIsAccessiblePlusLocked() {
        // Arrange — 국민연금만 있음
        given(pensionRepository.findMonthlyAmount(USER_ID, "NATIONAL"))
                .willReturn(Optional.of(new BigDecimal("500000")));
        given(holdingRepository.findHoldingsWithQuantityAndTypeByUserId(USER_ID))
                .willReturn(List.of());
        given(accountRepository.findByUserUserId(USER_ID))
                .willReturn(List.of());
        given(holdingRepository.sumUnrealizedGainLossByUserId(USER_ID))
                .willReturn(BigDecimal.ZERO);

        // Act
        AssetIncomeResponse response = service.getIncome(USER_ID);

        // Assert
        BigDecimal expectedTotal = response.getAccessibleIncome().add(response.getLockedIncome());
        assertThat(response.getTotalMonthlyIncome()).isEqualByComparingTo(expectedTotal);
    }

    @Test
    @DisplayName("연금계좌(IRP, PENSION_SAVING) 보유 ETF는 PENSION_DIVIDEND로 분류된다")
    void getIncome_pensionAccountHoldings_classifiedAsPensionDividend() {
        // Arrange — IRP와 PENSION_SAVING 각각 1개씩
        HoldingWithQuantityAndType irpHolding = mockHolding(201L, new BigDecimal("10"), "IRP");
        HoldingWithQuantityAndType savingHolding = mockHolding(202L, new BigDecimal("20"), "PENSION_SAVING");
        given(holdingRepository.findHoldingsWithQuantityAndTypeByUserId(USER_ID))
                .willReturn(List.of(irpHolding, savingHolding));
        given(pensionRepository.findMonthlyAmount(USER_ID, "NATIONAL"))
                .willReturn(Optional.empty());
        given(accountRepository.findByUserUserId(USER_ID))
                .willReturn(List.of());
        given(holdingRepository.sumUnrealizedGainLossByUserId(USER_ID))
                .willReturn(BigDecimal.ZERO);

        // ETF 연금 배당: 10*1000 + 20*2000 = 50,000
        given(productBatchClient.fetchEtfMonthlyDividends(List.of(201L, 202L)))
                .willReturn(Map.of(201L, new BigDecimal("1000"), 202L, new BigDecimal("2000")));

        // Act
        AssetIncomeResponse response = service.getIncome(USER_ID);

        // Assert
        assertThat(response.getLockedIncome()).isEqualByComparingTo("50000");
        assertThat(response.getAccessibleIncome()).isEqualByComparingTo("0");
        IncomeSource pensionDividend = findSource(response, "PENSION_DIVIDEND");
        assertThat(pensionDividend.getAmount()).isEqualByComparingTo("50000");
    }

    @Test
    @DisplayName("productId가 없는 DEPOSIT 계좌는 이자 계산에서 제외된다")
    void getIncome_depositAccountWithoutProductId_excluded() {
        // Arrange — productId null인 DEPOSIT 계좌
        Account depositAccountNoProduct = mock(Account.class);
        when(depositAccountNoProduct.getAccountType()).thenReturn("DEPOSIT");
        when(depositAccountNoProduct.getProductId()).thenReturn(null);

        given(pensionRepository.findMonthlyAmount(USER_ID, "NATIONAL"))
                .willReturn(Optional.empty());
        given(holdingRepository.findHoldingsWithQuantityAndTypeByUserId(USER_ID))
                .willReturn(List.of());
        given(accountRepository.findByUserUserId(USER_ID))
                .willReturn(List.of(depositAccountNoProduct));
        given(holdingRepository.sumUnrealizedGainLossByUserId(USER_ID))
                .willReturn(BigDecimal.ZERO);

        // Act
        AssetIncomeResponse response = service.getIncome(USER_ID);

        // Assert — DEPOSIT_INTEREST source가 없어야 함
        assertThat(response.getSources()).isEmpty();
        assertThat(response.getAccessibleIncome()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("totalUnrealizedGainLoss는 어떤 income 합산에도 포함되지 않는다")
    void getIncome_unrealizedGainLoss_notIncludedInIncomeSums() {
        // Arrange
        given(pensionRepository.findMonthlyAmount(USER_ID, "NATIONAL"))
                .willReturn(Optional.empty());
        given(holdingRepository.findHoldingsWithQuantityAndTypeByUserId(USER_ID))
                .willReturn(List.of());
        given(accountRepository.findByUserUserId(USER_ID))
                .willReturn(List.of());
        given(holdingRepository.sumUnrealizedGainLossByUserId(USER_ID))
                .willReturn(new BigDecimal("999999999"));

        // Act
        AssetIncomeResponse response = service.getIncome(USER_ID);

        // Assert — totalUnrealizedGainLoss는 income 합에 포함 안 됨
        assertThat(response.getTotalMonthlyIncome()).isEqualByComparingTo("0");
        assertThat(response.getTotalUnrealizedGainLoss()).isEqualByComparingTo("999999999");
    }

    // --- helpers ---

    private HoldingWithQuantityAndType mockHolding(Long productId, BigDecimal quantity, String accountType) {
        HoldingWithQuantityAndType h = mock(HoldingWithQuantityAndType.class);
        when(h.getProductId()).thenReturn(productId);
        when(h.getQuantity()).thenReturn(quantity);
        when(h.getAccountType()).thenReturn(accountType);
        return h;
    }

    private Account mockDepositAccount(Long productId, BigDecimal balance) {
        Account a = mock(Account.class);
        when(a.getAccountType()).thenReturn("DEPOSIT");
        when(a.getProductId()).thenReturn(productId);
        when(a.getDepositBalance()).thenReturn(balance);
        return a;
    }

    private IncomeSource findSource(AssetIncomeResponse response, String type) {
        return response.getSources().stream()
                .filter(s -> type.equals(s.getType()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Source not found: " + type));
    }
}
