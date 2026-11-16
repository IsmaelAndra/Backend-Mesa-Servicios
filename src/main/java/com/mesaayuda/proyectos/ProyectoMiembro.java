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

/** Persona del equipo de un proyecto (distinto de los equipos de atención). */
@Entity
@Table(name = "proyecto_miembro")
@Getter
@Setter
@NoArgsConstructor
public class ProyectoMiembro {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_proyecto_miembro", updatable = false)
    private UUID idProyectoMiembro;

    @Column(name = "proyecto_proyecto_miembro", nullable = false)
    private UUID proyectoId;

    @Column(name = "user_proyecto_miembro", nullable = false)
    private UUID userId;

    @Column(name = "rol_proyecto_miembro", nullable = false, length = 20)
    private String rol;

    @CreationTimestamp
    @Column(name = "created_at_proyecto_miembro", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

}
