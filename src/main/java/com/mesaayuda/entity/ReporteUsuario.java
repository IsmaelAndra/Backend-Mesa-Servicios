package com.mesaayuda.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Reporte hecho desde el modal: tabla, columnas y filtros (V31), nunca SQL. */
@Entity
@Table(name = "reporte_usuario")
@Getter
@Setter
@NoArgsConstructor
public class ReporteUsuario {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_reporte_usuario", updatable = false)
    private UUID idReporteUsuario;

    @Column(name = "nombre_reporte_usuario", nullable = false, length = 150)
    private String nombre;

    @Column(name = "descripcion_reporte_usuario", length = 500)
    private String descripcion;

    /** Icono (Font Awesome) y color de la tarjeta, como tenían los reportes por SQL (V32). */
    @Column(name = "icon_reporte_usuario", length = 100)
    private String icono;

    @Column(name = "color_reporte_usuario", length = 30)
    private String color;

    /** Quien lo creó; null en los reportes del sistema (V34). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "propietario_reporte_usuario")
    private User propietario;

    /** Reporte del sistema: lo ven todos los que ven reportes y solo lo edita el súper admin. */
    @Column(name = "sistema_reporte_usuario", nullable = false)
    private Boolean sistema = false;

    /** JSON de ConstructorConsulta.Definicion. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "definicion_reporte_usuario", nullable = false, columnDefinition = "jsonb")
    private String definicion;

    @CreationTimestamp
    @Column(name = "created_at_reporte_usuario", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at_reporte_usuario", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_reporte_usuario", nullable = false)
    private Boolean deleted = false;
}
