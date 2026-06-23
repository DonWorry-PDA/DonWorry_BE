package com.sol.user.mydata.service;

import com.sol.common.exception.BaseException;
import com.sol.user.account.entity.Account;
import com.sol.user.account.repository.AccountRepository;
import com.sol.user.cashflow.entity.CashFlowEvent;
import com.sol.user.cashflow.repository.CashFlowEventRepository;
import com.sol.user.holding.entity.Holding;
import com.sol.user.holding.repository.HoldingRepository;
import com.sol.user.mydata.dto.MydataTransactionsResponse;
import com.sol.user.pension.entity.Pension;
import com.sol.user.pension.repository.PensionRepository;
import com.sol.user.trade.entity.TradeHistory;
import com.sol.user.trade.repository.TradeHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MydataQueryServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private CashFlowEventRepository cashFlowEventRepository;

    @Mock
    private HoldingRepository holdingRepository;

    @Mock
    private PensionRepository pensionRepository;

    @Mock
    private TradeHistoryRepository tradeHistoryRepository;

    @InjectMocks
    private MydataQueryService mydataQueryService;

    @Test
    void getAccountsReturnsOnlyRequestedUsersAccounts() {
        Account account = mock(Account.class);
        when(account.getAccountId()).thenReturn(10L);
        when(account.getAccountType()).thenReturn("CHECKING_CMA");
        when(account.getInstitutionName()).thenReturn("신한은행");
        when(account.getAccountNumber()).thenReturn("110-123-456789");
        when(account.getDepositBalance()).thenReturn(BigDecimal.valueOf(1_000_000));
        when(account.getExistingAccount()).thenReturn(true);
        when(accountRepository.findByUserUserIdOrderByAccountIdAsc(1L)).thenReturn(List.of(account));

        var result = mydataQueryService.getAccounts(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).accountId()).isEqualTo(10L);
        assertThat(result.get(0).depositBalance()).isEqualByComparingTo("1000000");
        verify(accountRepository).findByUserUserIdOrderByAccountIdAsc(1L);
    }

    @Test
    void getTransactionsQueriesCashFlowsAndTradesWithInclusiveEndDate() {
        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 6, 30);

        CashFlowEvent cashFlow = mock(CashFlowEvent.class);
        when(cashFlow.getEventId()).thenReturn(20L);
        when(cashFlow.getEventDate()).thenReturn(LocalDate.of(2026, 6, 25));
        when(cashFlow.getEventType()).thenReturn("DIVIDEND");
        when(cashFlow.getAmount()).thenReturn(BigDecimal.valueOf(34_200));

        Account account = mock(Account.class);
        when(account.getAccountId()).thenReturn(10L);
        TradeHistory trade = mock(TradeHistory.class);
        when(trade.getOrderId()).thenReturn(30L);
        when(trade.getAccount()).thenReturn(account);
        when(trade.getTradeType()).thenReturn("BUY");
        when(trade.getTradedAt()).thenReturn(LocalDateTime.of(2026, 6, 30, 23, 59));

        when(cashFlowEventRepository
                .findByUserUserIdAndEventDateBetweenOrderByEventDateDescEventIdDesc(1L, from, to))
                .thenReturn(List.of(cashFlow));
        when(tradeHistoryRepository
                .findByAccountUserUserIdAndTradedAtGreaterThanEqualAndTradedAtLessThanOrderByTradedAtDescOrderIdDesc(
                        1L,
                        from.atStartOfDay(),
                        LocalDate.of(2026, 7, 1).atStartOfDay()
                ))
                .thenReturn(List.of(trade));

        MydataTransactionsResponse result = mydataQueryService.getTransactions(1L, from, to);

        assertThat(result.cashFlows()).hasSize(1);
        assertThat(result.cashFlows().get(0).eventType()).isEqualTo("DIVIDEND");
        assertThat(result.trades()).hasSize(1);
        assertThat(result.trades().get(0).accountId()).isEqualTo(10L);
    }

    @Test
    void getTransactionsRejectsReversedDateRange() {
        LocalDate from = LocalDate.of(2026, 7, 1);
        LocalDate to = LocalDate.of(2026, 6, 1);

        assertThatThrownBy(() -> mydataQueryService.getTransactions(1L, from, to))
                .isInstanceOf(BaseException.class);

        verify(cashFlowEventRepository, never())
                .findByUserUserIdAndEventDateBetweenOrderByEventDateDescEventIdDesc(1L, from, to);
    }

    @Test
    void getHoldingsIncludesOwningAccountId() {
        Account account = mock(Account.class);
        when(account.getAccountId()).thenReturn(10L);
        Holding holding = mock(Holding.class);
        when(holding.getHoldingId()).thenReturn(40L);
        when(holding.getAccount()).thenReturn(account);
        when(holding.getProductId()).thenReturn(100L);
        when(holdingRepository.findByAccountUserUserIdOrderByHoldingIdAsc(1L))
                .thenReturn(List.of(holding));

        var result = mydataQueryService.getHoldings(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).accountId()).isEqualTo(10L);
        assertThat(result.get(0).productId()).isEqualTo(100L);
    }

    @Test
    void getPensionsReturnsEmptyListWhenUserHasNoPensions() {
        when(pensionRepository.findByUserUserIdOrderByPensionIdAsc(1L)).thenReturn(List.of());

        var result = mydataQueryService.getPensions(1L);

        assertThat(result).isEmpty();
    }
}
