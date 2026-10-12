package com.mesaayuda.entity;
import java.util.UUID;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "microsoft_graph_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MicrosoftGraphConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_graph_config", updatable = false)
    private UUID idGraphConfig;
    @Column(name = "tenant_id", length = 150)
    private String tenantId;

    @Column(name = "client_id", length = 150)
    private String clientId;

    /** Client secret cifrado simétricamente (AES-GCM). Nunca texto plano. */
    @Column(name = "client_secret_encrypted", columnDefinition = "TEXT")
    private String clientSecretEncrypted;

    @Column(name = "scope", nullable = false, length = 500)
    private String scope = "https://graph.microsoft.com/.default";

    @Column(name = "grant_type", nullable = false, length = 50)
    private String grantType = "client_credentials";

    @Column(name = "secret_expires_at")
    private OffsetDateTime secretExpiresAt;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = false;

    @Column(name = "sync_interval_minutes", nullable = false)
    private Integer syncIntervalMinutes = 1440;

    /** Habilita el envío de notificaciones por correo usando Graph sendMail. */
    @Column(name = "mail_enabled", nullable = false)
    private Boolean mailEnabled = false;

    /** Cuenta de Microsoft 365 que actúa como remitente de los correos (sendmail de Graph). */
    @Column(name = "mail_from_account", length = 255)
    private String mailFromAccount;

    @Column(name = "last_sync_at")
    private OffsetDateTime lastSyncAt;

    @Column(name = "updated_by")
    private UUID updatedBy;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
