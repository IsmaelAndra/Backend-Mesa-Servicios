package com.mesaayuda.repository;

import com.mesaayuda.entity.NotificationSound;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NotificationSoundRepository extends JpaRepository<NotificationSound, UUID> {

    Optional<NotificationSound> findByScopeSoundAndKeySound(String scopeSound, String keySound);
}
