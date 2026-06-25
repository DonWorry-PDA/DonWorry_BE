package com.sol.user.trade.service;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.user.account.entity.Account;
import com.sol.user.account.repository.AccountRepository;
import com.sol.user.holding.entity.Holding;
import com.sol.user.holding.repository.HoldingRepository;
import com.sol.user.trade.dto.BuyRequest;
import com.sol.user.trade.dto.BuyResponse;
import com.sol.user.trade.entity.TradeHistory;
import com.sol.user.trade.infra.rest.EtfPriceClient;
import com.sol.user.trade.repository.TradeHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class TradeService {

    private final AccountRepository accountRepository;
    private final HoldingRepository holdingRepository;
    private final TradeHistoryRepository tradeHistoryRepository;
    private final EtfPriceClient etfPriceClient;

    @Transactional
    public BuyResponse buy(Long userId, BuyRequest request) {
        Account account = accountRepository.findByUserUserIdAndAccountType(userId, "BROKERAGE")
                .orElseThrow(() -> new BaseException(ErrorCode.BROKERAGE_ACCOUNT_NOT_FOUND));

        long rawPrice = etfPriceClient.getCurrentPrice(request.productId());
        BigDecimal currentPrice = BigDecimal.valueOf(rawPrice);
        BigDecimal totalAmount = request.quantity().multiply(currentPrice).setScale(0, RoundingMode.HALF_UP);

        if (account.getDepositBalance().compareTo(totalAmount) < 0) {
            throw new BaseException(ErrorCode.INSUFFICIENT_BALANCE);
        }

        TradeHistory trade = TradeHistory.ofBuy(account, request.productId(), request.quantity(), currentPrice);
        tradeHistoryRepository.save(trade);

        account.deductBalance(totalAmount);

        holdingRepository.findByAccountAccountIdAndProductId(account.getAccountId(), request.productId())
                .ifPresentOrElse(
                        existing -> existing.addPurchase(request.quantity(), currentPrice),
                        () -> holdingRepository.save(
                                Holding.ofBuy(account, request.productId(), request.quantity(), currentPrice))
                );

        return new BuyResponse(request.productId(), request.quantity(), rawPrice, totalAmount, trade.getTradedAt());
    }
}
