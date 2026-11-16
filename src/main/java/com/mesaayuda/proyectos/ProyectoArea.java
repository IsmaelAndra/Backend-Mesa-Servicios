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

/** Área invitada a un proyecto: PENDIENTE hasta que su jefe decide. */
@Entity
@Table(name = "proyecto_area")
@Getter
@Setter
@NoArgsConstructor
public class ProyectoArea {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_proyecto_area", updatable = false)
    private UUID idProyectoArea;

    @Column(name = "proyecto_proyecto_area", nullable = false)
    private UUID proyectoId;

    @Column(name = "area_proyecto_area", nullable = false)
    private UUID areaId;

    @Column(name = "estado_proyecto_area", nullable = false, length = 20)
    private String estado;

    @Column(name = "invitado_por_proyecto_area")
    private UUID invitadoPor;

    @Column(name = "decidido_por_proyecto_area")
    private UUID decididoPor;

    @Column(name = "decidido_at_proyecto_area")
    private OffsetDateTime decididoAt;

    @Column(name = "motivo_proyecto_area", length = 500)
    private String motivo;

    @CreationTimestamp
    @Column(name = "created_at_proyecto_area", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

}
