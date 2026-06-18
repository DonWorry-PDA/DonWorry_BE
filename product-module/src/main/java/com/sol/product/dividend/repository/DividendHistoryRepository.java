package com.sol.product.dividend.repository;

import com.sol.product.dividend.entity.DividendHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface DividendHistoryRepository extends JpaRepository<DividendHistory, Long> {

    Optional<DividendHistory> findByProductProductIdAndExDividendDate(Long productId, LocalDate exDividendDate);
}
