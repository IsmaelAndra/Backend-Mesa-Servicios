package com.mesaayuda.activos;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Tipo de activo (maestro, V36). El prefijo entra en el código: TI-LAP-000123. */
@Entity
@Table(name = "asset_type")
@Getter
@Setter
@NoArgsConstructor
public class AssetType {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_asset_type", updatable = false)
    private UUID idAssetType;

    @Column(name = "name_asset_type", nullable = false, length = 80)
    private String nombre;

    @Column(name = "prefix_asset_type", nullable = false, length = 10)
    private String prefijo;

    @Column(name = "icon_asset_type", length = 100)
    private String icono;

    @Column(name = "color_asset_type", length = 30)
    private String color;

    @Column(name = "active_asset_type", nullable = false)
    private Boolean activo = true;

    @CreationTimestamp
    @Column(name = "created_at_asset_type", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at_asset_type", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_asset_type", nullable = false)
    private Boolean deleted = false;

}
