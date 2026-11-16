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

/** Tarjeta del tablero. Su peso cuenta para el avance cuando está HECHO. */
@Entity
@Table(name = "proyecto_item")
@Getter
@Setter
@NoArgsConstructor
public class ProyectoItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_proyecto_item", updatable = false)
    private UUID idProyectoItem;

    @Column(name = "proyecto_proyecto_item", nullable = false)
    private UUID proyectoId;

    @Column(name = "sprint_proyecto_item")
    private UUID sprintId;

    @Column(name = "titulo_proyecto_item", nullable = false, length = 200)
    private String titulo;

    @Column(name = "descripcion_proyecto_item", columnDefinition = "text")
    private String descripcion;

    @Column(name = "estado_proyecto_item", nullable = false, length = 20)
    private String estado;

    @Column(name = "peso_proyecto_item", nullable = false)
    private Integer peso = 1;

    @Column(name = "responsable_proyecto_item")
    private UUID responsableId;

    @Column(name = "limite_proyecto_item")
    private LocalDate limite;

    @Column(name = "orden_proyecto_item", nullable = false)
    private Integer orden = 0;

    @Column(name = "creado_por_proyecto_item")
    private UUID creadoPor;

    @CreationTimestamp
    @Column(name = "created_at_proyecto_item", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at_proyecto_item", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_proyecto_item", nullable = false)
    private Boolean deleted = false;

}
