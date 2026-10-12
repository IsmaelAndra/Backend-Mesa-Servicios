package com.mesaayuda.repository;

import java.util.UUID;

import com.mesaayuda.entity.MfaRecoveryCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MfaRecoveryCodeRepository extends JpaRepository<MfaRecoveryCode, UUID> {

    List<MfaRecoveryCode> findByUser_IdUserAndUsadoAtIsNull(UUID userId);

    long countByUser_IdUserAndUsadoAtIsNull(UUID userId);
}
