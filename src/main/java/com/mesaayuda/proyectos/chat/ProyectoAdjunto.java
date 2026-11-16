package com.mesaayuda.proyectos.chat;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Archivo de un mensaje del chat de proyecto; vive en adjuntos/proyectos, nunca público. */
@Entity
@Table(name = "proyecto_adjunto")
@Getter
@Setter
@NoArgsConstructor
public class ProyectoAdjunto {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_proyecto_adjunto", updatable = false)
    private UUID idAdjunto;

    @Column(name = "mensaje_proyecto_adjunto", nullable = false)
    private UUID mensajeId;

    @Column(name = "nombre_proyecto_adjunto", nullable = false, length = 255)
    private String nombre;

    @Column(name = "ruta_proyecto_adjunto", nullable = false, length = 400)
    private String ruta;

    @Column(name = "mime_proyecto_adjunto", nullable = false, length = 120)
    private String mime;

    @Column(name = "tamano_proyecto_adjunto", nullable = false)
    private Long tamano;

    @CreationTimestamp
    @Column(name = "created_at_proyecto_adjunto", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
