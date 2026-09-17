package com.mesaayuda.repository;

import com.mesaayuda.entity.SlaEscalation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SlaEscalationRepository extends JpaRepository<SlaEscalation, Long> {

    List<SlaEscalation> findBySlaPolicyIdSlaPolicy(Long slaPolicyId);

    Optional<SlaEscalation> findBySlaPolicyIdSlaPolicyAndTriggerPercentage(Long slaPolicyId, Integer triggerPercentage);
}