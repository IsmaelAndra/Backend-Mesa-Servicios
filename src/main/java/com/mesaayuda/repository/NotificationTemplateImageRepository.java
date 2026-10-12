package com.mesaayuda.repository;

import java.util.UUID;

import com.mesaayuda.entity.NotificationTemplateImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NotificationTemplateImageRepository
        extends JpaRepository<NotificationTemplateImage, UUID> {

    Optional<NotificationTemplateImage> findByTokenImage(String tokenImage);

    long deleteByIdTemplate(UUID idTemplate);
}