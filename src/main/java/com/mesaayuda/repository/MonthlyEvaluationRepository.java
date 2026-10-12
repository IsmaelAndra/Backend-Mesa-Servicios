package com.mesaayuda.repository;

import java.util.UUID;

import com.mesaayuda.entity.MonthlyEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface MonthlyEvaluationRepository
        extends JpaRepository<MonthlyEvaluation, UUID>, JpaSpecificationExecutor<MonthlyEvaluation> {

    Optional<MonthlyEvaluation> findByAgentIdUserAndEvaluationMonth(UUID agentId, LocalDate month);

    List<MonthlyEvaluation> findByEvaluationMonthOrderBySatisfactionScoreDesc(LocalDate month);

    List<MonthlyEvaluation> findByAgentIdUserOrderByEvaluationMonthDesc(UUID agentId);

    Optional<MonthlyEvaluation> findTopByAgentIdUserOrderByEvaluationMonthDesc(UUID agentId);
}