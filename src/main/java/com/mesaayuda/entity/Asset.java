package com.mesaayuda.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "asset")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_asset", updatable = false)
    private UUID idAsset;

    @Column(name = "code_asset", nullable = false, unique = true, length = 50)
    private String codeAsset;

    @Column(name = "name_asset", nullable = false, length = 150)
    private String nameAsset;

    @Column(name = "type_asset", nullable = false, length = 50)
    private String typeAsset;

    @Column(name = "brand_asset", length = 100)
    private String brandAsset;

    @Column(name = "model_asset", length = 100)
    private String modelAsset;

    @Column(name = "serial_number_asset", length = 100)
    private String serialNumberAsset;

    @Column(name = "status_asset", nullable = false, length = 30)
    private String statusAsset = "DISPONIBLE";

    @Column(name = "purchase_date_asset")
    private OffsetDateTime purchaseDateAsset;

    @Column(name = "provider_asset", length = 150)
    private String providerAsset;

    @Column(name = "cost_asset", precision = 12, scale = 2)
    private BigDecimal costAsset;

    @Column(name = "warranty_until_asset")
    private OffsetDateTime warrantyUntilAsset;

    @Column(name = "image_url_asset", length = 500)
    private String imageUrlAsset;

    @Column(name = "document_url_asset", length = 500)
    private String documentUrlAsset;

    @Column(name = "notes_asset", columnDefinition = "TEXT")
    private String notesAsset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_user_id")
    private User assignedUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "area_id")
    private Area area;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sucursal_id")
    private Sucursal sucursal;

    @CreationTimestamp
    @Column(name = "created_at_asset", nullable = false, updatable = false)
    private OffsetDateTime createdAtAsset;

    @UpdateTimestamp
    @Column(name = "updated_at_asset", nullable = false)
    private OffsetDateTime updatedAtAsset;

    @Column(name = "deleted_asset", nullable = false)
    private Boolean deletedAsset = false;
}