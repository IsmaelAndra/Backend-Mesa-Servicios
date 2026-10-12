package com.mesaayuda.repository;

import java.util.UUID;

import com.mesaayuda.entity.MfaChallenge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface MfaChallengeRepository extends JpaRepository<MfaChallenge, UUID> {

    Optional<MfaChallenge> findByTokenHash(String tokenHash);

    /**
     * Invalida los desafíos vivos de un usuario.
     *
     * <p>Al pedir uno nuevo se anulan los anteriores. Sin esto, abrir dos pestañas
     * del login dejaría dos desafíos válidos y el usuario podría confirmar el
     * segundo mucho después de que el primero caducara, con la contraseña ya
     * expuesta durante todo ese rato.
     */
    @Modifying
    @Query("UPDATE MfaChallenge c SET c.usedAt = :ahora "
            + "WHERE c.user.idUser = :userId AND c.usedAt IS NULL")
    void invalidarVivos(@Param("userId") UUID userId, @Param("ahora") OffsetDateTime ahora);

    @Modifying
    @Query("DELETE FROM MfaChallenge c WHERE c.expiresAt < :ahora")
    int borrarVencidos(@Param("ahora") OffsetDateTime ahora);
}
