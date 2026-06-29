package com.sol.product.stock.realtime;

import com.sol.product.dailyprice.entity.DailyPrice;
import com.sol.product.dailyprice.repository.DailyPriceRepository;
import com.sol.product.stock.entity.StockDetail;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class ClosingPriceSaver {

    private final DailyPriceRepository dailyPriceRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(StockDetail detail, LocalDate today,
                     BigDecimal closingPrice, BigDecimal priceChange, BigDecimal changeRate) {
        Long productId = detail.getProduct().getProductId();
        dailyPriceRepository.deleteAllByProductProductId(productId);
        dailyPriceRepository.save(DailyPrice.builder()
                .product(detail.getProduct())
                .priceDate(today)
                .closingPrice(closingPrice)
                .priceChange(priceChange)
                .changeRate(changeRate)
                .build());
    }
}
