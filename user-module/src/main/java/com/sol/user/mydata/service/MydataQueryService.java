package com.sol.user.mydata.service;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.user.account.repository.AccountRepository;
import com.sol.user.cashflow.repository.CashFlowEventRepository;
import com.sol.user.holding.repository.HoldingRepository;
import com.sol.user.mydata.dto.MydataAccountResponse;
import com.sol.user.mydata.dto.MydataCashFlowResponse;
import com.sol.user.mydata.dto.MydataHoldingResponse;
import com.sol.user.mydata.dto.MydataPensionResponse;
import com.sol.user.mydata.dto.MydataTradeResponse;
import com.sol.user.mydata.dto.MydataTransactionsResponse;
import com.sol.user.pension.repository.PensionRepository;
import com.sol.user.trade.repository.TradeHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MydataQueryService {

    private final AccountRepository accountRepository;
    private final CashFlowEventRepository cashFlowEventRepository;
    private final HoldingRepository holdingRepository;
    private final PensionRepository pensionRepository;
    private final TradeHistoryRepository tradeHistoryRepository;

    public List<MydataAccountResponse> getAccounts(Long userId) {
        return accountRepository.findByUserUserIdOrderByAccountIdAsc(userId).stream()
                .map(MydataAccountResponse::from)
                .toList();
    }

    public MydataTransactionsResponse getTransactions(Long userId, LocalDate from, LocalDate to) {
        validateRange(from, to);

        List<MydataCashFlowResponse> cashFlows = cashFlowEventRepository
                .findByUserUserIdAndEventDateBetweenOrderByEventDateDescEventIdDesc(userId, from, to)
                .stream()
                .map(MydataCashFlowResponse::from)
                .toList();

        LocalDateTime fromInclusive = from.atStartOfDay();
        LocalDateTime toExclusive = to.plusDays(1).atStartOfDay();
        List<MydataTradeResponse> trades = tradeHistoryRepository
                .findByAccountUserUserIdAndTradedAtGreaterThanEqualAndTradedAtLessThanOrderByTradedAtDescOrderIdDesc(
                        userId,
                        fromInclusive,
                        toExclusive
                )
                .stream()
                .map(MydataTradeResponse::from)
                .toList();

        return new MydataTransactionsResponse(cashFlows, trades);
    }

    public List<MydataHoldingResponse> getHoldings(Long userId) {
        return holdingRepository.findByAccountUserUserIdOrderByHoldingIdAsc(userId).stream()
                .map(MydataHoldingResponse::from)
                .toList();
    }

    public List<MydataPensionResponse> getPensions(Long userId) {
        return pensionRepository.findByUserUserIdOrderByPensionIdAsc(userId).stream()
                .map(MydataPensionResponse::from)
                .toList();
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) {
            throw new BaseException(ErrorCode.INVALID_INPUT);
        }
    }
}
