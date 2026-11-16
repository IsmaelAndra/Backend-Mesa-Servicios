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

/** Configuración de activos (fila única): numeración y datos del formulario del acta. */
@Entity
@Table(name = "asset_config")
@Getter
@Setter
@NoArgsConstructor
public class AssetConfig {
    @Id
    @Column(name = "id_asset_config", updatable = false)
    private UUID idAssetConfig;

    /** Tokens: {TIPO} prefijo del tipo, {ANIO} año, {NUM:n} número con n dígitos. */
    @Column(name = "formato_codigo_config", nullable = false, length = 60)
    private String formatoCodigo;

    @Column(name = "contador_por_tipo_config", nullable = false)
    private Boolean contadorPorTipo = true;

    @Column(name = "formato_acta_config", nullable = false, length = 60)
    private String formatoActa;

    @Column(name = "ciudad_config", nullable = false, length = 80)
    private String ciudad;

    @Column(name = "codigo_formulario_config", nullable = false, length = 40)
    private String codigoFormulario;

    @Column(name = "version_formulario_config", nullable = false, length = 20)
    private String versionFormulario;

    @Column(name = "dias_aviso_garantia_config", nullable = false)
    private Integer diasAvisoGarantia = 30;

    @UpdateTimestamp
    @Column(name = "updated_at_asset_config", nullable = false)
    private OffsetDateTime updatedAt;

}
