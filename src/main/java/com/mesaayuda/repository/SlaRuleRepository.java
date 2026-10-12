package com.mesaayuda.repository;

import java.util.UUID;

import com.mesaayuda.entity.SlaRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SlaRuleRepository extends JpaRepository<SlaRule, UUID> {

    Optional<SlaRule> findByAreaIdAreaAndPriorityIdPriorityAndCategoryIdCategoryAndTypeIdTicketTypeAndActiveSlaRuleTrue(
            UUID areaId, UUID priorityId, UUID categoryId, UUID typeId);

    Optional<SlaRule> findByAreaIdAreaAndPriorityIdPriorityAndCategoryIdCategoryAndTypeIsNullAndActiveSlaRuleTrue(
            UUID areaId, UUID priorityId, UUID categoryId);

    Optional<SlaRule> findByAreaIdAreaAndPriorityIdPriorityAndCategoryIsNullAndTypeIdTicketTypeAndActiveSlaRuleTrue(
            UUID areaId, UUID priorityId, UUID typeId);

    Optional<SlaRule> findByAreaIdAreaAndPriorityIdPriorityAndCategoryIsNullAndTypeIsNullAndActiveSlaRuleTrue(
            UUID areaId, UUID priorityId);

    List<SlaRule> findByAreaIdAreaAndPriorityIdPriorityAndActiveSlaRuleTrue(
            UUID areaId, UUID priorityId);

    List<SlaRule> findByActiveSlaRuleTrue();
}
