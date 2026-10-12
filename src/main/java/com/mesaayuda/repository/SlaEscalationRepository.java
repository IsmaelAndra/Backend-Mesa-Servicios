package com.mesaayuda.repository;

import java.util.UUID;

import com.mesaayuda.entity.SlaEscalation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SlaEscalationRepository extends JpaRepository<SlaEscalation, UUID> {

    List<SlaEscalation> findBySlaPolicyIdSlaPolicy(UUID slaPolicyId);

    Optional<SlaEscalation> findBySlaPolicyIdSlaPolicyAndTriggerPercentage(UUID slaPolicyId, Integer triggerPercentage);
}