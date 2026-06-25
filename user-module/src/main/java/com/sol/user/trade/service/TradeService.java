package com.sol.user.trade.service;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.user.account.entity.Account;
import com.sol.user.account.repository.AccountRepository;
import com.sol.user.holding.entity.Holding;
import com.sol.user.holding.repository.HoldingRepository;
import com.sol.user.trade.dto.BuyRequest;
import com.sol.user.trade.dto.BuyResponse;
import com.sol.user.trade.dto.TransferRequest;
import com.sol.user.trade.dto.TransferResponse;
import com.sol.user.trade.entity.TradeHistory;
import com.sol.user.trade.infra.rest.EtfPriceClient;
import com.sol.user.trade.repository.TradeHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class TradeService {

    private final AccountRepository accountRepository;
    private final HoldingRepository holdingRepository;
    private final TradeHistoryRepository tradeHistoryRepository;
    private final EtfPriceClient etfPriceClient;

    @Transactional
    public BuyResponse buy(Long userId, BuyRequest request) {
        Account account = accountRepository.findByUserUserIdAndAccountTypeForUpdate(userId, "BROKERAGE")
                .orElseThrow(() -> new BaseException(ErrorCode.BROKERAGE_ACCOUNT_NOT_FOUND));

        long rawPrice = etfPriceClient.getCurrentPrice(request.productId());
        BigDecimal currentPrice = BigDecimal.valueOf(rawPrice);
        BigDecimal totalAmount = request.quantity().multiply(currentPrice).setScale(0, RoundingMode.HALF_UP);

        account.deductBalance(totalAmount);

        TradeHistory trade = TradeHistory.ofBuy(account, request.productId(), request.quantity(), currentPrice);
        tradeHistoryRepository.save(trade);

        holdingRepository.findByAccountAccountIdAndProductId(account.getAccountId(), request.productId())
                .ifPresentOrElse(
                        existing -> existing.addPurchase(request.quantity(), currentPrice),
                        () -> holdingRepository.save(
                                Holding.ofBuy(account, request.productId(), request.quantity(), currentPrice))
                );

        return new BuyResponse(request.productId(), request.quantity(), rawPrice, totalAmount, trade.getTradedAt());
    }

    @Transactional
    public TransferResponse transfer(Long userId, TransferRequest request) {
        // 엔티티 로딩 없이 ID만 조회 — 이후 PESSIMISTIC_WRITE 경로에서 처음 로딩되도록 해 stale 캐시 방지
        Long brokerageAccountId = accountRepository.findAccountIdByUserIdAndAccountType(userId, "BROKERAGE")
                .orElseThrow(() -> new BaseException(ErrorCode.BROKERAGE_ACCOUNT_NOT_FOUND));

        List<Long> fromIds = request.transfers().stream()
                .map(TransferRequest.TransferItem::fromAccountId)
                .distinct()
                .toList();

        if (fromIds.contains(brokerageAccountId)) {
            throw new BaseException(ErrorCode.INVALID_INPUT);
        }

        // 모든 계좌를 accountId 오름차순으로 한 번에 락 (deadlock 방지)
        List<Long> allIds = Stream.concat(fromIds.stream(), Stream.of(brokerageAccountId))
                .sorted()
                .toList();

        List<Account> locked = accountRepository.findAllByIdAndUserIdForUpdate(allIds, userId);
        if (locked.size() != allIds.size()) {
            // 소유하지 않은 계좌가 포함된 경우
            throw new BaseException(ErrorCode.INVALID_INPUT);
        }

        Map<Long, Account> byId = locked.stream()
                .collect(Collectors.toMap(Account::getAccountId, a -> a));

        BigDecimal total = BigDecimal.ZERO;
        for (TransferRequest.TransferItem item : request.transfers()) {
            byId.get(item.fromAccountId()).deductBalance(item.amount());
            total = total.add(item.amount());
        }

        Account brokerageAccount = byId.get(brokerageAccountId);
        brokerageAccount.addBalance(total);

        return new TransferResponse(brokerageAccount.getDepositBalance(), LocalDateTime.now());
    }
}
