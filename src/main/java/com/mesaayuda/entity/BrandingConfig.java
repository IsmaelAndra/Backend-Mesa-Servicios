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
@Table(name = "branding_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BrandingConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_branding", updatable = false)
    private UUID idBranding;
    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "background_url", length = 500)
    private String backgroundUrl;

    @Column(name = "logo_login_url", length = 500)
    private String logoLoginUrl;

    @Column(name = "system_icon_url", length = 500)
    private String systemIconUrl;

    /** Imagen de la cabecera de los reportes PDF; si falta, se usa logo_url. */
    @Column(name = "report_logo_url", length = 500)
    private String reportLogoUrl;

    @Column(name = "profile_cover_url", length = 500)
    private String profileCoverUrl;

    @Column(name = "chat_background_url", length = 500)
    private String chatBackgroundUrl;

    @Column(name = "chat_background_light_url", length = 500)
    private String chatBackgroundLightUrl;

    @Column(name = "chat_background_dark_url", length = 500)
    private String chatBackgroundDarkUrl;

    @Column(name = "primary_color", nullable = false, length = 7)
    private String primaryColor = "#062e86";

    @Column(name = "secondary_color", nullable = false, length = 7)
    private String secondaryColor = "#ec9811";

    /**
     * Modo mantenimiento: deja la aplicacion en pagina de aviso para que el
     * administrador la ponga cuando un sistema de la institucion no funciona.
     * Sin esto habia que editar codigo y recompilar.
     */
    @Column(name = "maintenance_mode", nullable = false)
    private Boolean maintenanceMode = false;

    /** Titulo de la pantalla de mantenimiento. */
    @Column(name = "maintenance_title", length = 160)
    private String maintenanceTitle;

    /** Mensaje que ve el usuario. */
    @Column(name = "maintenance_message", length = 1000)
    private String maintenanceMessage;

    /** Fin estimado de la interrupcion, en texto libre (por ejemplo "18:00"). */
    @Column(name = "maintenance_eta", length = 160)
    private String maintenanceEta;

    /** Logo de la institucion que se muestra en la pantalla de aviso. */
    @Column(name = "maintenance_logo_url", length = 500)
    private String maintenanceLogoUrl;

    /** Clase de Font Awesome del icono grande del aviso (p. ej. fa-solid fa-gear). */
    @Column(name = "maintenance_icon", length = 120)
    private String maintenanceIcon;

    /** Color del icono del aviso, en hexadecimal. */
    @Column(name = "maintenance_icon_color", length = 7)
    private String maintenanceIconColor;

    // ================= PANTALLA DE CARGA (SPLASH) =================
    // Se muestra mientras la aplicacion arranca. AUTO usa el icono del
    // sistema y el color primario de la marca.

    /** AUTO, IMAGE o ICON: que se dibuja en el centro del splash. */
    @Column(name = "loading_mode", nullable = false, length = 20)
    private String loadingMode = "AUTO";

    /** Imagen propia del splash; si falta, se usa el icono del sistema. */
    @Column(name = "loading_image_url", length = 500)
    private String loadingImageUrl;

    /** Clase de Font Awesome del splash (p. ej. fa-solid fa-gear). */
    @Column(name = "loading_icon", length = 120)
    private String loadingIcon;

    /** Color de fondo del splash; por defecto el color primario. */
    @Column(name = "loading_background_color", length = 7)
    private String loadingBackgroundColor;

    /** Color del titulo y del mensaje del splash. */
    @Column(name = "loading_text_color", length = 7)
    private String loadingTextColor;

    /** Linea principal del splash; por defecto el nombre de la aplicacion. */
    @Column(name = "loading_title", length = 160)
    private String loadingTitle;

    /** Linea secundaria; por defecto "Cargando...". */
    @Column(name = "loading_message", length = 160)
    private String loadingMessage;

    /** SPINNER, DOTS, BARS, PULSE o NONE: animacion del indicador. */
    @Column(name = "loading_animation", nullable = false, length = 20)
    private String loadingAnimation = "SPINNER";

    /**
     * Efecto que se aplica al logo o al icono del splash: que se desvanezca y
     * aparezca, que pulse, flote, se amplie, etc. Es independiente del
     * indicador, para poder combinar "logo con latido" sin circulo de carga.
     */
    @Column(name = "loading_logo_effect", nullable = false, length = 20)
    private String loadingLogoEffect = "NONE";

    /** Muestra una barra de progreso indeterminate bajo el contenido. */
    @Column(name = "loading_progress", nullable = false)
    private Boolean loadingProgress = false;

    /** LIGHT o DARK: la variante con la que se pinta el splash. */
    @Column(name = "loading_theme", nullable = false, length = 10)
    private String loadingTheme = "LIGHT";

    /** Tiempo minimo en pantalla, en ms, para que no parpadee al arrancar. */
    @Column(name = "loading_min_duration_ms", nullable = false)
    private Integer loadingMinDurationMs = 1200;

    /**
     * Splash en modo "solo el logo": se ve unicamente la marca elegida, sin el
     * titulo, el mensaje ni el indicador de carga. Es lo que se quiere en
     * cuanto el administrador sube su propia imagen o escoge un icono, en vez
     * de un "Cargando..." encima de su logo.
     */
    @Column(name = "loading_solo_marca", nullable = false)
    private Boolean loadingSoloMarca = false;

    /** Lado (ancho y alto) en px del logo del splash. */
    @Column(name = "loading_image_size", nullable = false)
    private Integer loadingImageSize = 112;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}