package com.mesaayuda.repository;

import com.mesaayuda.entity.SlaRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SlaRuleRepository extends JpaRepository<SlaRule, Long> {

    Optional<SlaRule> findByAreaIdAreaAndPriorityIdPriorityAndCategoryIdCategoryAndTypeIdTicketTypeAndActiveSlaRuleTrue(
            Long areaId, Long priorityId, Long categoryId, Long typeId);

    Optional<SlaRule> findByAreaIdAreaAndPriorityIdPriorityAndCategoryIdCategoryAndTypeIsNullAndActiveSlaRuleTrue(
            Long areaId, Long priorityId, Long categoryId);

    Optional<SlaRule> findByAreaIdAreaAndPriorityIdPriorityAndCategoryIsNullAndTypeIdTicketTypeAndActiveSlaRuleTrue(
            Long areaId, Long priorityId, Long typeId);

    Optional<SlaRule> findByAreaIdAreaAndPriorityIdPriorityAndCategoryIsNullAndTypeIsNullAndActiveSlaRuleTrue(
            Long areaId, Long priorityId);

    List<SlaRule> findByAreaIdAreaAndPriorityIdPriorityAndActiveSlaRuleTrue(
            Long areaId, Long priorityId);

    List<SlaRule> findByActiveSlaRuleTrue();
}
