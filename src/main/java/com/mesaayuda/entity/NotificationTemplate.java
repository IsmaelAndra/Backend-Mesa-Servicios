package com.mesaayuda.entity;
import java.util.UUID;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "notification_template")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NotificationTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_template", updatable = false)
    private UUID idTemplate;
    @Column(name = "name_template", nullable = false, unique = true, length = 100)
    private String nameTemplate;

    @Column(name = "type_template", nullable = false, length = 50)
    private String typeTemplate;

    @Column(name = "subject_template", nullable = false, length = 255)
    private String subjectTemplate;

    @Column(name = "body_template", nullable = false, columnDefinition = "text")
    private String bodyTemplate;

    @Column(name = "active_template", nullable = false)
    private Boolean activeTemplate = true;

    @Column(name = "icon_template", length = 100)
    private String iconTemplate;

    @Column(name = "color_template", length = 30)
    private String colorTemplate;

    /**
     * Imagen propia de la plantilla, para usarla dentro del HTML del correo con
     * la variable [imagen] (o [imagenUrl] si se prefiere la direccion suelta).
     */
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    /** Quién recibe el correo para esta plantilla: SOLICITANTE | AGENTE | AMBOS | CUSTOM | DEFAULT. */
    @Column(name = "recipient_rule", nullable = false, length = 50)
    private String recipientRule = "DEFAULT";

    /** Correo fijo usado cuando {@code recipientRule} es CUSTOM. */
    @Column(name = "recipient_email", length = 255)
    private String recipientEmail;

    /**
     * Marca de plantilla editada por un administrador. Los scripts de arranque
     * solo rellenan el cuerpo y el destinatario de las que siguen siendo
     * semilla, así que en cuanto se guarda desde la pantalla de plantillas
     * queda marcada y un reinicio ya no la toca.
     */
@Column(name = "custom_template", nullable = false)
private Boolean customTemplate = false;

@CreationTimestamp
    @Column(name = "created_at_template", nullable = false, updatable = false)
    private OffsetDateTime createdAtTemplate;

    @UpdateTimestamp
    @Column(name = "updated_at_template", nullable = false)
    private OffsetDateTime updatedAtTemplate;
}