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
import java.util.List;

@Service
@RequiredArgsConstructor
public class SavedPortfolioPlanService {

    private final SavedPortfolioPlanRepository savedPlanRepository;
    private final UserRepository userRepository;
    private final SavedPortfolioPlanMapper savedPortfolioPlanMapper;

    @Transactional
    public SavePlanResponse save(Long userId, SavePlanRequest request) {
        LocalDateTime now = LocalDateTime.now();

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

        return savedPortfolioPlanMapper.toSaveResponse(plan.getId(), now);
    }

    @Transactional(readOnly = true)
    public List<SavedPlanResponse> getSavedList(Long userId) {
        return savedPlanRepository.findByUserUserId(userId).stream()
                .map(savedPortfolioPlanMapper::toSavedPlanResponse)
                .toList();
    }

    @Transactional
    public void delete(Long userId, Long planId) {
        SavedPortfolioPlan plan = savedPlanRepository.findByIdAndUserUserId(planId, userId)
                .orElseThrow(() -> new BaseException(ErrorCode.SAVED_PLAN_NOT_FOUND));
        savedPlanRepository.delete(plan);
    }
}
