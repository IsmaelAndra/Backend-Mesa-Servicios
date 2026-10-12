package com.mesaayuda.repository;

import java.util.UUID;

import com.mesaayuda.entity.NotificationTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, UUID> {
    Optional<NotificationTemplate> findByNameTemplateAndActiveTemplateTrue(String nameTemplate);
    Optional<NotificationTemplate> findByNameTemplate(String nameTemplate);
    boolean existsByNameTemplate(String nameTemplate);
}