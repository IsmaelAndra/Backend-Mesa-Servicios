package com.mesaayuda.entity;

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
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_graph_config", updatable = false)
    private Long idGraphConfig;

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

    @Column(name = "last_sync_at")
    private OffsetDateTime lastSyncAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
