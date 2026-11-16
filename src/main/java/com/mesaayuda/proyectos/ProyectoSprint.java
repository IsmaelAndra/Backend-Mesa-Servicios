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

/** Sprint de un proyecto. */
@Entity
@Table(name = "proyecto_sprint")
@Getter
@Setter
@NoArgsConstructor
public class ProyectoSprint {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_proyecto_sprint", updatable = false)
    private UUID idProyectoSprint;

    @Column(name = "proyecto_proyecto_sprint", nullable = false)
    private UUID proyectoId;

    @Column(name = "nombre_proyecto_sprint", nullable = false, length = 120)
    private String nombre;

    @Column(name = "objetivo_proyecto_sprint", length = 500)
    private String objetivo;

    @Column(name = "inicio_proyecto_sprint")
    private LocalDate inicio;

    @Column(name = "fin_proyecto_sprint")
    private LocalDate fin;

    @Column(name = "estado_proyecto_sprint", nullable = false, length = 20)
    private String estado;

    @CreationTimestamp
    @Column(name = "created_at_proyecto_sprint", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "deleted_proyecto_sprint", nullable = false)
    private Boolean deleted = false;

}
