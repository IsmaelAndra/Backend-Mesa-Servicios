package com.mesaayuda.entity;
import java.util.UUID;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "refresh_token")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_refresh_token", updatable = false)
    private UUID idRefreshToken;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "token_hash", nullable = false, unique = true, length = 128)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "revoked", nullable = false)
    private Boolean revoked = false;

    /**
     * Hash del token que sustituyo a este.
     *
     * <p>Permite detectar el robo por reutilizacion. El flujo normal es rotar:
     * cada refresh emite un par nuevo y anula el anterior. Si un token ya rotado
     * vuelve a usarse, no es un fallo de red ni un doble clic, hay dos manos con
     * el mismo token y la segunda es la sospechosa. Con esta referencia se
     * localiza la cadena completa y se cierran todas las sesiones del usuario.
     */
    @Column(name = "replaced_by_hash", length = 128)
    private String replacedByHash;

    /** Cuando se detecto la reutilizacion, para no repetir el aviso cada vez. */
    @Column(name = "reuse_detected_at")
    private OffsetDateTime reuseDetectedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}