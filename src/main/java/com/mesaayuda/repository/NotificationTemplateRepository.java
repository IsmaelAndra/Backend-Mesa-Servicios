package com.mesaayuda.repository;

import com.mesaayuda.entity.NotificationTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, Long> {
    Optional<NotificationTemplate> findByNameTemplateAndActiveTemplateTrue(String nameTemplate);
    boolean existsByNameTemplate(String nameTemplate);
}