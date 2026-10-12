package com.mesaayuda.entity;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * Desafío de un solo uso del doble factor.
 *
 * <p>Existe porque, con el MFA activo, el login NO puede devolver tokens: solo
 * devuelve un desafío. Los tokens salen al confirmar el código. Sin este paso
 * intermedio, el segundo factor no impediría nada, porque la contraseña sola ya
 * bastaría.
 *
 * <p>Se guarda el hash y no el token, por el mismo motivo que en el
 * restablecimiento de contraseña: si alguien lee la base, los desafíos pendientes
 * no le sirven.
 */
@Entity
@Table(name = "mfa_challenge")
public class MfaChallenge {

    /** El usuario se.login correctamente pero todavía no ha probado el código. */
    public static final String PROPOSITO_LOGIN = "LOGIN";

    /** El usuario aún no está enrolado y hay que confirmar el alta. */
    public static final String PROPOSITO_ENROLL = "ENROLL";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_mfa_challenge")
    private UUID idMfaChallenge;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 128)
    private String tokenHash;

    /** {@link #PROPOSITO_LOGIN} o {@link #PROPOSITO_ENROLL}. */
    @Column(name = "purpose", nullable = false, length = 16)
    private String purpose;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "used_at")
    private OffsetDateTime usedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public UUID getIdMfaChallenge() {
        return idMfaChallenge;
    }

    public void setIdMfaChallenge(UUID idMfaChallenge) {
        this.idMfaChallenge = idMfaChallenge;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(OffsetDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public OffsetDateTime getUsedAt() {
        return usedAt;
    }

    public void setUsedAt(OffsetDateTime usedAt) {
        this.usedAt = usedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
