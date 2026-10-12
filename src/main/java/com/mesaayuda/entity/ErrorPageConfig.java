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
 * Textos de las pantallas de error 404, 403, 500 y 503.
 *
 * Es una tabla singleton: el id esta fijado en 1 y un CHECK en la base impide
 * que se inserte una segunda fila. No hay "@GeneratedValue" a proposito, igual
 * que en SessionConfig, para que el id sea siempre 1 y no dependa de una
 * secuencia que podria quedar desalineada tras una importacion de datos.
 *
 * Cada estado tiene su propio par titulo/mensaje porque el texto que sirve para
 * un 404 no sirve para un 500: el 500 tiene que dar confianza de que el equipo
 * ya fue avisado, y el 403 tiene que decir que el permiso se puede solicitar.
 */
@Entity
@Table(name = "error_page_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ErrorPageConfig {

    @Id
    @Column(name = "id_error_page", updatable = false, nullable = false)
    private UUID idErrorPage = IdsConfiguracion.ERROR_PAGE;
    // ---------- 404: la ruta no existe ----------

    @Column(name = "error_404_title", nullable = false, length = 160)
    private String error404Title = "Página no encontrada";

    @Column(name = "error_404_message", nullable = false, length = 1000)
    private String error404Message =
            "La dirección que buscas no existe o fue movida. Revisa el enlace o vuelve al inicio.";

    @Column(name = "error_404_icon", nullable = false, length = 120)
    private String error404Icon = "fa-solid fa-compass";

    @Column(name = "error_404_color", nullable = false, length = 7)
    private String error404Color = "#64748b";

    // ---------- 403: el rol no alcanza ----------

    @Column(name = "error_403_title", nullable = false, length = 160)
    private String error403Title = "Acceso no autorizado";

    @Column(name = "error_403_message", nullable = false, length = 1000)
    private String error403Message =
            "Tu perfil no tiene permiso para ver esta sección. Si crees que es un error, "
            + "solicita el acceso al administrador.";

    @Column(name = "error_403_icon", nullable = false, length = 120)
    private String error403Icon = "fa-solid fa-lock";

    @Column(name = "error_403_color", nullable = false, length = 7)
    private String error403Color = "#d97706";

    // ---------- 500: fallo del servidor ----------

    @Column(name = "error_500_title", nullable = false, length = 160)
    private String error500Title = "Error interno del servidor";

    @Column(name = "error_500_message", nullable = false, length = 1000)
    private String error500Message =
            "Ocurrió un problema inesperado en el servidor. El equipo técnico ya fue notificado.";

    @Column(name = "error_500_icon", nullable = false, length = 120)
    private String error500Icon = "fa-solid fa-triangle-exclamation";

    @Column(name = "error_500_color", nullable = false, length = 7)
    private String error500Color = "#dc2626";

    // ---------- 503: mantenimiento ----------

    @Column(name = "error_503_title", nullable = false, length = 160)
    private String error503Title = "Servicio no disponible";

    @Column(name = "error_503_message", nullable = false, length = 1000)
    private String error503Message =
            "El sistema está temporalmente fuera de servicio por mantenimiento. "
            + "Vuelve a intentar en unos minutos.";

    @Column(name = "error_503_icon", nullable = false, length = 120)
    private String error503Icon = "fa-solid fa-screwdriver-wrench";

    @Column(name = "error_503_color", nullable = false, length = 7)
    private String error503Color = "#2563eb";

    // ----- Comunes a todas las páginas de aviso (V9) -----

    /** Texto del botón principal ("Volver al inicio"). */
    @Column(name = "error_button_text", nullable = false, length = 60)
    private String errorButtonText = "Volver al inicio";

    /** Si se muestra el código grande ("404") junto al icono. */
    @Column(name = "error_show_code", nullable = false)
    private Boolean errorShowCode = true;

    /** Si aparece el enlace a la política de privacidad. */
    @Column(name = "error_show_privacy", nullable = false)
    private Boolean errorShowPrivacy = true;

    @Column(name = "updated_by")
    private UUID updatedBy;
    @CreationTimestamp
    @Column(name = "created_at_error", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at_error", nullable = false)
    private OffsetDateTime updatedAt;
}