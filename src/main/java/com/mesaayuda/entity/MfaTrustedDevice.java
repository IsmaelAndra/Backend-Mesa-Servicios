package com.mesaayuda.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Equipo (navegador) recordado tras confirmar el doble factor: durante su
 * vigencia el login no vuelve a pedir el código en él. Se guarda solo el
 * SHA-256 del token; el token en claro vive en una cookie HttpOnly.
 */
@Entity
@Table(name = "mfa_trusted_device")
@Getter
@Setter
@NoArgsConstructor
public class MfaTrustedDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_trusted_device", updatable = false)
    private UUID idTrustedDevice;

    @Column(name = "user_trusted_device", nullable = false, updatable = false)
    private UUID userTrustedDevice;

    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    private String tokenHash;

    @Column(name = "user_agent_device", length = 200)
    private String userAgentDevice;

    @Column(name = "expires_at_device", nullable = false)
    private OffsetDateTime expiresAtDevice;

    @Column(name = "last_used_at_device")
    private OffsetDateTime lastUsedAtDevice;

    @Column(name = "created_at_device", nullable = false, updatable = false)
    private OffsetDateTime createdAtDevice;
}
