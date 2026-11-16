package com.mesaayuda.plantillas;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Versión de un área de una plantilla de correo (V38). {@code nombre} es el
 * nombre de la plantilla general (TICKET_CREADO...): si el área que atiende el
 * ticket tiene una activa, el correo sale con ella.
 */
@Entity
@Table(name = "plantilla_area")
@Getter
@Setter
@NoArgsConstructor
public class PlantillaArea {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_plantilla_area", updatable = false)
    private UUID idPlantillaArea;

    @Column(name = "nombre_plantilla_area", nullable = false, length = 100)
    private String nombre;

    @Column(name = "area_plantilla_area", nullable = false)
    private UUID areaId;

    @Column(name = "asunto_plantilla_area", nullable = false, length = 255)
    private String asunto;

    @Column(name = "cuerpo_plantilla_area", nullable = false, columnDefinition = "TEXT")
    private String cuerpo;

    @Column(name = "activa_plantilla_area", nullable = false)
    private Boolean activa = true;

    @Column(name = "editada_por_plantilla_area")
    private UUID editadaPor;

    @CreationTimestamp
    @Column(name = "created_at_plantilla_area", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at_plantilla_area", nullable = false)
    private OffsetDateTime updatedAt;
}
