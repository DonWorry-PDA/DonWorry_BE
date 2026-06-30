package com.sol.user.portfolio.service;

import com.sol.common.exception.BaseException;
import com.sol.common.exception.ErrorCode;
import com.sol.user.portfolio.dto.SavePlanRequest;
import com.sol.user.portfolio.dto.SavePlanResponse;
import com.sol.user.portfolio.dto.SavedPlanResponse;
import com.sol.user.portfolio.entity.SavedPortfolioPlan;
import com.sol.user.portfolio.mapper.SavedPortfolioPlanMapper;
import com.sol.user.portfolio.repository.SavedPortfolioPlanRepository;
import com.sol.user.user.entity.User;
import com.sol.user.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SavedPortfolioPlanService {

    private final SavedPortfolioPlanRepository savedPlanRepository;
    private final UserRepository userRepository;
    private final SavedPortfolioPlanMapper savedPortfolioPlanMapper;

    @Transactional
    public SavePlanResponse save(Long userId, SavePlanRequest request) {
        LocalDateTime now = LocalDateTime.now();
        Optional<SavedPortfolioPlan> existing = savedPlanRepository.findByUserUserId(userId);

        if (existing.isPresent()) {
            SavedPortfolioPlan plan = existing.get();
            plan.update(request.planType(), request.monthlyIncome(),
                    request.currentCoverageRate(), request.totalCoverageRate(),
                    request.currentMonthlyShortfall(), request.residualMonthlyShortfall(),
                    request.principalAmount(), now);
            request.holdings().stream()
                    .map(savedPortfolioPlanMapper::toItem)
                    .forEach(plan::addItem);
            return savedPortfolioPlanMapper.toSaveResponse(now);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

        SavedPortfolioPlan plan = SavedPortfolioPlan.builder()
                .user(user)
                .planType(request.planType())
                .monthlyIncome(request.monthlyIncome())
                .currentCoverageRate(request.currentCoverageRate())
                .totalCoverageRate(request.totalCoverageRate())
                .currentMonthlyShortfall(request.currentMonthlyShortfall())
                .residualMonthlyShortfall(request.residualMonthlyShortfall())
                .principalAmount(request.principalAmount())
                .savedAt(now)
                .build();
        request.holdings().stream()
                .map(savedPortfolioPlanMapper::toItem)
                .forEach(plan::addItem);
        savedPlanRepository.save(plan);

        return savedPortfolioPlanMapper.toSaveResponse(now);
    }

    @Transactional(readOnly = true)
    public SavedPlanResponse getSaved(Long userId) {
        return savedPlanRepository.findByUserUserId(userId)
                .map(savedPortfolioPlanMapper::toSavedPlanResponse)
                .orElse(null);
    }

    @Transactional
    public void delete(Long userId) {
        savedPlanRepository.findByUserUserId(userId)
                .ifPresent(savedPlanRepository::delete);
    }
}
