package com.mesaayuda.repository;

import java.util.UUID;

import com.mesaayuda.entity.MfaSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MfaSettingRepository extends JpaRepository<MfaSetting, UUID> {

    Optional<MfaSetting> findById(UUID id);
}
