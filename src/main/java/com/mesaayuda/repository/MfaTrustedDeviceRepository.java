package com.mesaayuda.repository;

import com.mesaayuda.entity.MfaTrustedDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface MfaTrustedDeviceRepository extends JpaRepository<MfaTrustedDevice, UUID> {

    Optional<MfaTrustedDevice> findByTokenHash(String tokenHash);

    /** Olvida todos los equipos de una persona (cambio de contraseña, MFA desactivado). */
    @Modifying
    @Query("delete from MfaTrustedDevice d where d.userTrustedDevice = :userId")
    int olvidarDe(@Param("userId") UUID userId);
}
