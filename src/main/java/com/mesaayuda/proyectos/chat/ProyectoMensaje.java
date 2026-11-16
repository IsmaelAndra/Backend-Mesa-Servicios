package com.mesaayuda.proyectos.chat;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Mensaje del chat grupal de un proyecto (V39). */
@Entity
@Table(name = "proyecto_mensaje")
@Getter
@Setter
@NoArgsConstructor
public class ProyectoMensaje {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_proyecto_mensaje", updatable = false)
    private UUID idMensaje;

    @Column(name = "proyecto_proyecto_mensaje", nullable = false)
    private UUID proyectoId;

    @Column(name = "autor_proyecto_mensaje", nullable = false)
    private UUID autorId;

    @Column(name = "texto_proyecto_mensaje", columnDefinition = "TEXT")
    private String texto;

    @Column(name = "deleted_proyecto_mensaje", nullable = false)
    private Boolean deleted = false;

    @CreationTimestamp
    @Column(name = "created_at_proyecto_mensaje", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** Última edición (el chat lo marca "editado"); null si nunca se editó. V45. */
    @Column(name = "editado_at_proyecto_mensaje")
    private OffsetDateTime editadoAt;

    /** Mensaje al que responde (cita), del mismo proyecto. V45. */
    @Column(name = "responde_a_proyecto_mensaje")
    private UUID respondeA;
}
