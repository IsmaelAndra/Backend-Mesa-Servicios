package com.mesaayuda.repository;

import java.util.UUID;

import com.mesaayuda.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /**
     * Invalida los enlaces pendientes de un usuario.
     *
     * <p>Se llama cuando el propio usuario pide un restablecimiento: pedir uno
     * nuevo debe tapar el anterior. Sin esto se acumulan varios correos vivos y
     * el que llegue tarde sigue valiendo.
     */
    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.used = true WHERE t.user.id = :userId AND t.used = false")
    void invalidarPendientes(@Param("userId") UUID userId);

    /** Limpieza de enlaces ya vencidos, para que la tabla no crezca sin fin. */
    @Modifying
    @Query("DELETE FROM PasswordResetToken t WHERE t.expiresAt < :ahora")
    int borrarVencidos(@Param("ahora") OffsetDateTime ahora);
}