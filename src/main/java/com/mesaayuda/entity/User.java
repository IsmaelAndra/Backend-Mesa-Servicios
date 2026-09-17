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
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_user", updatable = false)
    private Long idUser;

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

    public String getFullName() {
        if (displayName != null && !displayName.isBlank()) {
            return displayName;
        }
        return corporateEmailUser;
    }
}