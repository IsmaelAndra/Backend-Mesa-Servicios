package com.mesaayuda.entity;
import java.util.ArrayList;
import java.util.List;
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
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_user", updatable = false)
    private UUID idUser;
    @Column(name = "identification_user", nullable = false, unique = true, length = 30)
    private String identificationUser;

    @Column(name = "corporate_email_user", nullable = false, unique = true, length = 150)
    private String corporateEmailUser;

    @Column(name = "password_user", nullable = false)
    private String passwordUser;

    @Column(name = "phone_user", length = 30)
    private String phoneUser;

    @Column(name = "status_user", nullable = false, length = 30)
    private String statusUser = "ACTIVE";

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "rol_user", nullable = false)
    private Role rolUser;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "area_id", nullable = false)
    private Area area;

    /**
     * Sede fisica donde trabaja la persona. Opcional a proposito: las cuentas
     * antigas y las que se dan de alta sin sede siguen siendo validas.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sucursal_id")
    private Sucursal sucursal;

    @Column(name = "must_change_password", nullable = false)
    private Boolean mustChangePassword = false;

    @Column(name = "temporal_password_expires_at")
    private OffsetDateTime temporalPasswordExpiresAt;

    @CreationTimestamp
    @Column(name = "created_at_user", nullable = false, updatable = false)
    private OffsetDateTime createdAtUser;

    @UpdateTimestamp
    @Column(name = "updated_at_user", nullable = false)
    private OffsetDateTime updatedAtUser;

    @Column(name = "deleted_user", nullable = false)
    private Boolean deletedUser = false;

    // ============================ MICROSOFT 365 (Graph) ============================

    @Column(name = "display_name", length = 255)
    private String displayName;

    @Column(name = "first_name", length = 150)
    private String firstName;

    @Column(name = "last_name", length = 150)
    private String lastName;

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    /** Extensión telefónica en la central (V13). Solo dígitos. */
    @Column(name = "extension_user", length = 20)
    private String extensionUser;

    @Column(name = "user_principal_name", length = 255)
    private String userPrincipalName;

    @Column(name = "microsoft_active")
    private Boolean microsoftActive;

    @Column(name = "microsoft_photo_etag", length = 255)
    private String microsoftPhotoEtag;

    @Column(name = "microsoft_synced", nullable = false)
    private Boolean microsoftSynced = false;

    @Column(name = "last_sync")
    private OffsetDateTime lastSync;

    /** Los tokens de acceso emitidos antes de este instante ya no valen. */
    @Column(name = "tokens_valid_after")
    private OffsetDateTime tokensValidAfter;

    @OneToMany(mappedBy = "assignedUser", fetch = FetchType.LAZY)
    private List<Asset> assignedAssets = new ArrayList<>();

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private List<AssetHistory> assetHistory = new ArrayList<>();

    public String getFullName() {
        if (displayName != null && !displayName.isBlank()) {
            return displayName;
        }
        return corporateEmailUser;
    }
}