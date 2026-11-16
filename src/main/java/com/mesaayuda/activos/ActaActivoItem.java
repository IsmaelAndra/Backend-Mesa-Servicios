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

/** Equipo de un acta, con la foto de sus datos al momento de firmarla. */
@Entity
@Table(name = "acta_activo_item")
@Getter
@Setter
@NoArgsConstructor
public class ActaActivoItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_acta_activo_item", updatable = false)
    private UUID idItem;

    @Column(name = "acta_acta_activo_item", nullable = false)
    private UUID actaId;

    @Column(name = "asset_acta_activo_item", nullable = false)
    private UUID assetId;

    @Column(name = "principal_acta_activo_item", nullable = false)
    private Boolean principal = false;

    @Column(name = "orden_acta_activo_item", nullable = false)
    private Integer orden = 0;

    @Column(name = "codigo_acta_activo_item", nullable = false, length = 50)
    private String codigo;

    @Column(name = "tipo_acta_activo_item", length = 80)
    private String tipoEquipo;

    @Column(name = "nombre_acta_activo_item", length = 150)
    private String nombre;

    @Column(name = "marca_modelo_acta_activo_item", length = 210)
    private String marcaModelo;

    @Column(name = "serie_acta_activo_item", length = 100)
    private String serie;

    @Column(name = "estado_fisico_acta_activo_item", length = 20)
    private String estadoFisico;

    @Column(name = "accesorios_acta_activo_item", columnDefinition = "text")
    private String accesorios;

    @Column(name = "configuracion_acta_activo_item", columnDefinition = "text")
    private String configuracion;

    @Column(name = "novedades_acta_activo_item", columnDefinition = "text")
    private String novedades;

}
