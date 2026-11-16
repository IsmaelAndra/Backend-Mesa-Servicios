package com.mesaayuda.proyectos;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Proyecto de un área (V35). Otras áreas participan si su jefe acepta. */
@Entity
@Table(name = "proyecto")
@Getter
@Setter
@NoArgsConstructor
public class Proyecto {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_proyecto", updatable = false)
    private UUID idProyecto;

    @Column(name = "nombre_proyecto", nullable = false, length = 150)
    private String nombre;

    @Column(name = "descripcion_proyecto", columnDefinition = "text")
    private String descripcion;

    /** Área dueña: la del jefe que lo creó. */
    @Column(name = "area_proyecto", nullable = false)
    private UUID areaId;

    @Column(name = "estado_proyecto", nullable = false, length = 20)
    private String estado;

    @Column(name = "icon_proyecto", length = 100)
    private String icono;

    @Column(name = "color_proyecto", length = 30)
    private String color;

    @Column(name = "inicio_proyecto")
    private LocalDate inicio;

    @Column(name = "fin_proyecto")
    private LocalDate fin;

    @Column(name = "creado_por_proyecto")
    private UUID creadoPor;

    @CreationTimestamp
    @Column(name = "created_at_proyecto", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at_proyecto", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_proyecto", nullable = false)
    private Boolean deleted = false;

}
