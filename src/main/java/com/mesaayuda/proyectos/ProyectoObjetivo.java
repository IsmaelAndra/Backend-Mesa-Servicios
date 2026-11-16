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

/** Objetivo del proyecto; cumplido (logradoAt) es un logro. */
@Entity
@Table(name = "proyecto_objetivo")
@Getter
@Setter
@NoArgsConstructor
public class ProyectoObjetivo {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_proyecto_objetivo", updatable = false)
    private UUID idProyectoObjetivo;

    @Column(name = "proyecto_proyecto_objetivo", nullable = false)
    private UUID proyectoId;

    @Column(name = "descripcion_proyecto_objetivo", nullable = false, length = 500)
    private String descripcion;

    @Column(name = "logrado_at_proyecto_objetivo")
    private OffsetDateTime logradoAt;

    @Column(name = "nota_logro_proyecto_objetivo", length = 1000)
    private String notaLogro;

    @Column(name = "orden_proyecto_objetivo", nullable = false)
    private Integer orden = 0;

    @CreationTimestamp
    @Column(name = "created_at_proyecto_objetivo", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "deleted_proyecto_objetivo", nullable = false)
    private Boolean deleted = false;

}
