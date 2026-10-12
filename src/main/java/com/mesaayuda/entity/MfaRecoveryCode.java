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
 * Código de recuperación para cuando el usuario pierde el móvil.
 *
 * <p>Sin esto, el doble factor se convierte en una forma de perder la cuenta:
 * quien no tenga el teléfono se queda fuera y hay que ir a consola. Se guardan
 * cifrados y se marcan uno a uno como usados, para que se sepa cuáles quedan sin
 * tener que descifrar todos en cada consulta.
 */
@Entity
@Table(name = "mfa_recovery_code")
public class MfaRecoveryCode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_mfa_recovery")
    private UUID idMfaRecovery;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "code_hash", nullable = false, length = 128)
    private String codeHash;

    @Column(name = "usado_at")
    private OffsetDateTime usadoAt;

    @Column(name = "creado_at_mfa", nullable = false)
    private OffsetDateTime creadoAtMfa;

    public UUID getIdMfaRecovery() {
        return idMfaRecovery;
    }

    public void setIdMfaRecovery(UUID idMfaRecovery) {
        this.idMfaRecovery = idMfaRecovery;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public void setCodeHash(String codeHash) {
        this.codeHash = codeHash;
    }

    public OffsetDateTime getUsadoAt() {
        return usadoAt;
    }

    public void setUsadoAt(OffsetDateTime usadoAt) {
        this.usadoAt = usadoAt;
    }

    public OffsetDateTime getCreadoAtMfa() {
        return creadoAtMfa;
    }

    public void setCreadoAtMfa(OffsetDateTime creadoAtMfa) {
        this.creadoAtMfa = creadoAtMfa;
    }
}
