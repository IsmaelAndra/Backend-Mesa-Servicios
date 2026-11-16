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

/** Foto o documento (garantía, factura) de un activo. Nunca se sirve como estático. */
@Entity
@Table(name = "asset_archivo")
@Getter
@Setter
@NoArgsConstructor
public class AssetArchivo {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_asset_archivo", updatable = false)
    private UUID idAssetArchivo;

    @Column(name = "asset_asset_archivo", nullable = false)
    private UUID assetId;

    @Column(name = "tipo_asset_archivo", nullable = false, length = 20)
    private String tipo;

    @Column(name = "nombre_asset_archivo", nullable = false, length = 255)
    private String nombre;

    @Column(name = "ruta_asset_archivo", nullable = false, length = 500)
    private String ruta;

    @Column(name = "mime_asset_archivo", nullable = false, length = 100)
    private String mime;

    @Column(name = "tamano_asset_archivo", nullable = false)
    private Long tamano;

    @Column(name = "vence_asset_archivo")
    private LocalDate vence;

    @Column(name = "subido_por_asset_archivo")
    private UUID subidoPor;

    @CreationTimestamp
    @Column(name = "created_at_asset_archivo", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "deleted_asset_archivo", nullable = false)
    private Boolean deleted = false;

}
