package com.sol.user.asset.service;

import com.sol.user.account.entity.Account;
import com.sol.user.account.repository.AccountRepository;
import com.sol.user.asset.dto.AssetBreakdown;
import com.sol.user.holding.dto.HoldingWithProduct;
import com.sol.user.holding.repository.HoldingRepository;
import com.sol.user.portfolio.infra.rest.ProductBatchClient;
import com.sol.user.portfolio.infra.rest.ProductBatchItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class AssetAggregatorTest {

    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final HoldingRepository holdingRepository = mock(HoldingRepository.class);
    private final ProductBatchClient productBatchClient = mock(ProductBatchClient.class);

    private final AssetAggregator aggregator =
            new AssetAggregator(accountRepository, holdingRepository, productBatchClient);

    @Test
    void 제외목록을_적용하면_해당_계좌_예수금과_보유종목이_집계에서_빠진다() {
        stubData();

        // 계좌2(예금 5천만)와 보유종목11(ETF 3억) 제외
        AssetBreakdown breakdown = aggregator.aggregate(1L, Set.of(2L), Set.of(11L));

        // cash = 증권 예수금 1억 (계좌2 5천만 제외)
        assertThat(breakdown.cash()).isEqualByComparingTo("100000000");
        // nonStock = ETF 2억 (보유종목11 3억 제외)
        assertThat(breakdown.nonStockHoldingValue()).isEqualByComparingTo("200000000");
        // operatingTotal = 1억 + 2억 = 3억
        assertThat(breakdown.operatingTotal()).isEqualByComparingTo("300000000");
    }

    @Test
    void 제외목록이_없으면_전체가_집계된다() {
        stubData();

        AssetBreakdown breakdown = aggregator.aggregate(1L);

        // cash = 증권 1억 + 예금 5천만 = 1.5억
        assertThat(breakdown.cash()).isEqualByComparingTo("150000000");
        // nonStock = ETF 2억 + 3억 = 5억
        assertThat(breakdown.nonStockHoldingValue()).isEqualByComparingTo("500000000");
        assertThat(breakdown.operatingTotal()).isEqualByComparingTo("650000000");
    }

    private void stubData() {
        Account brokerage = mockAccount(1L, "BROKERAGE", 100_000_000);
        Account deposit = mockAccount(2L, "DEPOSIT", 50_000_000);
        given(accountRepository.findByUserUserId(1L)).willReturn(List.of(brokerage, deposit));

        HoldingWithProduct h1 = mockHolding(10L, 100L, "BROKERAGE", 200_000_000);
        HoldingWithProduct h2 = mockHolding(11L, 101L, "BROKERAGE", 300_000_000);
        given(holdingRepository.findHoldingsWithAccountTypeByUserId(1L)).willReturn(List.of(h1, h2));

        given(productBatchClient.fetchProducts(any())).willReturn(Map.of(
                100L, new ProductBatchItem(100L, "SOL ETF A", "ETF"),
                101L, new ProductBatchItem(101L, "SOL ETF B", "ETF")));
    }

    private Account mockAccount(Long accountId, String accountType, long deposit) {
        Account account = mock(Account.class);
        given(account.getAccountId()).willReturn(accountId);
        given(account.getAccountType()).willReturn(accountType);
        given(account.getDepositBalance()).willReturn(BigDecimal.valueOf(deposit));
        return account;
    }

    private HoldingWithProduct mockHolding(Long holdingId, Long productId, String accountType, long eval) {
        HoldingWithProduct holding = mock(HoldingWithProduct.class);
        given(holding.getHoldingId()).willReturn(holdingId);
        given(holding.getProductId()).willReturn(productId);
        given(holding.getAccountType()).willReturn(accountType);
        given(holding.getEvaluationAmount()).willReturn(BigDecimal.valueOf(eval));
        return holding;
    }
}
