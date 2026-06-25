package com.sol.user.consultation.repository;

import com.sol.user.consultation.entity.ConsultationSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConsultationSummaryRepository extends JpaRepository<ConsultationSummary, Long> {

    Optional<ConsultationSummary> findByConsultationId(Long consultationId);

    void deleteByConsultationIdIn(List<Long> consultationIds);
}
