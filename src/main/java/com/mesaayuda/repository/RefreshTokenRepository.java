package com.mesaayuda.repository;

import java.util.UUID;

import com.mesaayuda.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.userId = :userId AND rt.revoked = false")
    void revokeAllByUser(@Param("userId") UUID userId);

    /**
     * Última renovación de sesión de cada usuario, en una sola consulta para no
     * recorrer la lista. created_at se escribe en cada rotación del token (que
     * ocurre al renovar el token de acceso), así que sirve como marca de actividad.
     * Devuelve pares [userId, createdAt].
     */
    @Query("SELECT rt.userId, MAX(rt.createdAt) FROM RefreshToken rt "
            + "WHERE rt.userId IN :userIds AND rt.revoked = false AND rt.expiresAt > :ahora "
            + "GROUP BY rt.userId")
    List<Object[]> ultimaActividadPorUsuario(@Param("userIds") Collection<UUID> userIds,
                                              @Param("ahora") OffsetDateTime ahora);
}