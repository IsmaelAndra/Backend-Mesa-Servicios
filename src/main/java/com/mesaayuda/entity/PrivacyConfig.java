package com.mesaayuda.entity;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

/**
 * Politica de privacidad que muestra el sistema, editable desde el panel.
 *
 * El cuerpo se escribe en markdown y lo renderiza el frontend, asi que va como
 * TEXT. No lleva "@Lob": en PostgreSQL un byte[] con @Lob se acaba guardando en
 * la tabla grande de objetos binarios y leerlo por getBytes da problemas; el
 * texto se queda en la columna normal y es mucho mas comodo de indexar y editar.
 *
 * "customPrivacy" es lo que protege el contenido: mientras valga FALSE, el seed
 * de data.sql puede actualizar el texto si el repositorio trae una version mas
 * nueva. En cuanto el super administrador guarda algo, pasa a TRUE y el seed ya
 * no interviene. Sin esta bandera, cada reinicio del servidor devolveria el
 * texto a la version del repositorio y perderian las ediciones.
 */
@Entity
@Table(name = "privacy_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PrivacyConfig {

    @Id
    @Column(name = "id_privacy_config", updatable = false, nullable = false)
    private UUID idPrivacyConfig = IdsConfiguracion.PRIVACIDAD;
    @Column(name = "privacy_title", nullable = false, length = 160)
    private String privacyTitle = "Politica de privacidad";

    /** Version del documento, por ejemplo "1.0" o "2.1". La muestra la pantalla. */
    @Column(name = "privacy_version", nullable = false, length = 30)
    private String privacyVersion = "1.0";

    @Column(name = "privacy_content", columnDefinition = "text")
    private String privacyContent;

    /** Correo o texto de contacto para dudas sobre el tratamiento de datos. */
    @Column(name = "privacy_contact", length = 160)
    private String privacyContact;

    /** Fecha de entrada en vigor, en texto libre ("15 de enero de 2026"). */
    @Column(name = "privacy_effective", length = 60)
    private String privacyEffective;

    @Column(name = "custom_privacy", nullable = false)
    private Boolean customPrivacy = false;

    @Column(name = "updated_by")
    private UUID updatedBy;
    @CreationTimestamp
    @Column(name = "created_at_privacy", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at_privacy", nullable = false)
    private OffsetDateTime updatedAt;
}