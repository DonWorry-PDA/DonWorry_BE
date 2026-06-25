package com.sol.user.consultation.repository;

import com.sol.user.consultation.entity.Consultation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConsultationRepository extends JpaRepository<Consultation, Long> {

    List<Consultation> findByUserIdOrderByScheduledAtDesc(Long userId);

    List<Consultation> findByUserId(Long userId);

    Optional<Consultation> findByIdAndUserId(Long id, Long userId);
}
