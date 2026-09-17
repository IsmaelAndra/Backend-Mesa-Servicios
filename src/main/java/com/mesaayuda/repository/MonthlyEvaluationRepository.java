package com.mesaayuda.repository;

import com.mesaayuda.entity.MonthlyEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface MonthlyEvaluationRepository
        extends JpaRepository<MonthlyEvaluation, Long>, JpaSpecificationExecutor<MonthlyEvaluation> {

    Optional<MonthlyEvaluation> findByAgentIdUserAndEvaluationMonth(Long agentId, LocalDate month);

    List<MonthlyEvaluation> findByEvaluationMonthOrderBySatisfactionScoreDesc(LocalDate month);

    List<MonthlyEvaluation> findByAgentIdUserOrderByEvaluationMonthDesc(Long agentId);

    Optional<MonthlyEvaluation> findTopByAgentIdUserOrderByEvaluationMonthDesc(Long agentId);
}