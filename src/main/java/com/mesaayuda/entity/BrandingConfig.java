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
@Table(name = "branding_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BrandingConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_branding", updatable = false)
    private Long idBranding;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "background_url", length = 500)
    private String backgroundUrl;

    @Column(name = "primary_color", nullable = false, length = 7)
    private String primaryColor = "#062e86";

    @Column(name = "secondary_color", nullable = false, length = 7)
    private String secondaryColor = "#ec9811";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}