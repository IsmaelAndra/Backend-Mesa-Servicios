package com.mesaayuda.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Interruptor global del doble factor. Tabla de una sola fila (id fijo 1).
 *
 * <p>Se guarda en la base y no en el yml para que el super administrador pueda
 * activarlo sin reiniciar, que es la diferencia entre poder reaccionar a una
 * incidencia y tener que esperar al siguiente despliegue.
 */
@Entity
@Table(name = "mfa_setting")
public class MfaSetting {

/** Id fijo: una sola fila. */
    public static final UUID UNICO = IdsConfiguracion.MFA;

    @Id
    @Column(name = "id_mfa_setting")
    private UUID id;

    @Column(name = "mfa_enabled", nullable = false)
    private boolean mfaEnabled = false;

    /** Nombre que verá el usuario en su app de autenticación. */
    @Column(name = "issuer_mfa", nullable = false)
    private String issuerMfa = "Mesa de Servicios";

    @Column(name = "updated_at_mfa", nullable = false)
    private OffsetDateTime updatedAtMfa;

    @Column(name = "updated_by_mfa")
    private UUID updatedByMfa;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public boolean isMfaEnabled() {
        return mfaEnabled;
    }

    public void setMfaEnabled(boolean mfaEnabled) {
        this.mfaEnabled = mfaEnabled;
    }

    public String getIssuerMfa() {
        return issuerMfa;
    }

    public void setIssuerMfa(String issuerMfa) {
        this.issuerMfa = issuerMfa;
    }

    public OffsetDateTime getUpdatedAtMfa() {
        return updatedAtMfa;
    }

    public void setUpdatedAtMfa(OffsetDateTime updatedAtMfa) {
        this.updatedAtMfa = updatedAtMfa;
    }

public UUID getUpdatedByMfa() {
        return updatedByMfa;
    }

    public void setUpdatedByMfa(UUID updatedByMfa) {
        this.updatedByMfa = updatedByMfa;
    }
}
