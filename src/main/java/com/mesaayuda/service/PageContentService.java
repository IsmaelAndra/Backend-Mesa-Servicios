package com.mesaayuda.service;

import com.mesaayuda.dto.ErrorPageConfigRequest;
import com.mesaayuda.dto.ErrorPageConfigResponse;
import com.mesaayuda.dto.PrivacyConfigRequest;
import com.mesaayuda.dto.PrivacyConfigResponse;
import com.mesaayuda.entity.ErrorPageConfig;
import com.mesaayuda.entity.PrivacyConfig;
import com.mesaayuda.repository.ErrorPageConfigRepository;
import com.mesaayuda.repository.PrivacyConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Textos de las pantallas de error y politica de privacidad, editables.
 *
 * Estos textos los lee una pantalla que puede ver cualquiera, incluso con el
 * servidor en mantenimiento o con un 403 de por medio, asi que la lectura no
 * puede depender de que haya sesion ni de que la base de datos responda con
 * normalidad. Por eso:
 *
 * - la lectura publica se cachea unos segundos, para no golpear la base en cada
 *   navegacion, y porque un texto de error que tarda en cargar no sirve;
 * - si la tabla esta vacia o la consulta falla, se devuelven los valores por
 *   defecto de la entidad en vez de propagar el error. Una pantalla de 500 que
 *   falla al pedir su propio texto dejaria al usuario con una pantalla en
 *   blanco, que es peor que un texto generico.
 *
 * La escritura si exige super administrador y es la que invalida la cache.
 */
@Service
public class PageContentService {

    private static final Logger log = LoggerFactory.getLogger(PageContentService.class);

    /** Segundos que se mantiene la copia en memoria del texto publico. */
    private static final long CACHE_MS = 5_000L;

    @Autowired private ErrorPageConfigRepository errorPageConfigRepository;
    @Autowired private PrivacyConfigRepository privacyConfigRepository;

    /**
     * Copia en memoria de lo que se sirve al publico. Es volatile porque la
     * lectura viene de hilos de peticiones HTTP y la escritura del hilo que
     * guarda el formulario, asi que tiene que verse de inmediato.
     */
    private volatile ErrorPageConfigResponse cacheErrores;
    private volatile long cacheErroresEn;

    private volatile PrivacyConfigResponse cachePrivacidad;
    private volatile long cachePrivacidadEn;

    // ============================ ERRORES ============================

    /**
     * Textos de error para la vista publica. Nunca lanza: si algo falla se
     * devuelven los valores por defecto.
     */
    @Transactional(readOnly = true)
    public ErrorPageConfigResponse textosDeError() {
        ErrorPageConfigResponse enCache = cacheErrores;
        if (enCache != null && (System.currentTimeMillis() - cacheErroresEn) < CACHE_MS) {
            return enCache;
        }
        try {
            ErrorPageConfig entity = errorPageConfigRepository.findTopByOrderByIdErrorPageAsc()
                    .orElseGet(ErrorPageConfig::new);
            ErrorPageConfigResponse respuesta = aRespuesta(entity);
            cacheErrores = respuesta;
            cacheErroresEn = System.currentTimeMillis();
            return respuesta;
        } catch (Exception e) {
            // Se registra, pero no se propaga: ver el comentario de la clase.
            log.warn("No se pudieron leer los textos de error, se usan los de defecto: {}", e.getMessage());
            return aRespuesta(new ErrorPageConfig());
        }
    }

    /**
     * Guarda los textos de error del panel. Solo los campos presentes se
     * tocan, y los iconos o colores que llegan vacios caen al valor por defecto
     * del estado en vez de dejar la pantalla sin icono o con color negro.
     */
    @Transactional
    public ErrorPageConfigResponse guardarTextosDeError(ErrorPageConfigRequest request, UUID updatedBy) {
        ErrorPageConfig actual = errorPageConfigRepository.findTopByOrderByIdErrorPageAsc()
                .orElseGet(ErrorPageConfig::new);

        actual.setError404Title(request.error404Title().trim());
        actual.setError404Message(request.error404Message().trim());
        actual.setError404Icon(iconoONull(request.error404Icon(), actual.getError404Icon(), "fa-solid fa-compass"));
        actual.setError404Color(colorONull(request.error404Color(), "#64748b"));

        actual.setError403Title(request.error403Title().trim());
        actual.setError403Message(request.error403Message().trim());
        actual.setError403Icon(iconoONull(request.error403Icon(), actual.getError403Icon(), "fa-solid fa-lock"));
        actual.setError403Color(colorONull(request.error403Color(), "#d97706"));

        actual.setError500Title(request.error500Title().trim());
        actual.setError500Message(request.error500Message().trim());
        actual.setError500Icon(iconoONull(request.error500Icon(), actual.getError500Icon(), "fa-solid fa-triangle-exclamation"));
        actual.setError500Color(colorONull(request.error500Color(), "#dc2626"));

        actual.setError503Title(request.error503Title().trim());
        actual.setError503Message(request.error503Message().trim());
        actual.setError503Icon(iconoONull(request.error503Icon(), actual.getError503Icon(), "fa-solid fa-screwdriver-wrench"));
        actual.setError503Color(colorONull(request.error503Color(), "#2563eb"));

        // Ajustes comunes: solo se tocan si llegan (un cliente antiguo no los manda).
        if (request.errorButtonText() != null && !request.errorButtonText().isBlank()) {
            actual.setErrorButtonText(request.errorButtonText().trim());
        }
        if (request.errorShowCode() != null) {
            actual.setErrorShowCode(request.errorShowCode());
        }
        if (request.errorShowPrivacy() != null) {
            actual.setErrorShowPrivacy(request.errorShowPrivacy());
        }

        actual.setUpdatedBy(updatedBy);
        ErrorPageConfig guardada = errorPageConfigRepository.save(actual);

        invalidarCache();
        return aRespuesta(guardada);
    }

    // ============================ PRIVACIDAD ============================

    /**
     * Politica de privacidad para la vista publica. Como los textos de error,
     * nunca lanza: un backend caido tiene que poder explicar que esta caido.
     */
    @Transactional(readOnly = true)
    public PrivacyConfigResponse politicaDePrivacidad() {
        PrivacyConfigResponse enCache = cachePrivacidad;
        if (enCache != null && (System.currentTimeMillis() - cachePrivacidadEn) < CACHE_MS) {
            return enCache;
        }
        try {
            PrivacyConfig entity = privacyConfigRepository.findTopByOrderByIdPrivacyConfigAsc()
                    .orElseGet(PrivacyConfig::new);
            return cachear(entity);
        } catch (Exception e) {
            log.warn("No se pudo leer la politica de privacidad, se usan los de defecto: {}", e.getMessage());
            return cachear(new PrivacyConfig());
        }
    }

    /**
     * Guarda la politica de privacidad.
     *
     * Al guardar, customPrivacy pasa a TRUE aunque el administrador no lo haya
     * tocado: a partir de ese momento el texto es suyo y el seed de data.sql no
     * vuelve a imponer el del repositorio.
     */
    @Transactional
    public PrivacyConfigResponse guardarPoliticaDePrivacidad(PrivacyConfigRequest request, UUID updatedBy) {
        PrivacyConfig actual = privacyConfigRepository.findTopByOrderByIdPrivacyConfigAsc()
                .orElseGet(PrivacyConfig::new);

        actual.setPrivacyTitle(request.privacyTitle().trim());
        actual.setPrivacyVersion(vacioONull(request.privacyVersion()));
        // Un cuerpo vacio se guarda como cadena vacia, no como null: asi el
        // administrador ve exactamente lo que escribio y no "undefined".
        actual.setPrivacyContent(request.privacyContent() == null ? "" : request.privacyContent().trim());
        actual.setPrivacyContact(vacioONull(request.privacyContact()));
        actual.setPrivacyEffective(vacioONull(request.privacyEffective()));
        actual.setCustomPrivacy(true);
        actual.setUpdatedBy(updatedBy);

        PrivacyConfig guardada = privacyConfigRepository.save(actual);

        invalidarCache();
        return cachear(guardada);
    }

    /** Descarta las copias en memoria; se llama al guardar o al invalidar desde el panel. */
    public void invalidarCache() {
        cacheErrores = null;
        cacheErroresEn = 0L;
        cachePrivacidad = null;
        cachePrivacidadEn = 0L;
    }

    // ============================ CONVERSIONES ============================

    private PrivacyConfigResponse cachear(PrivacyConfig entity) {
        PrivacyConfigResponse respuesta = new PrivacyConfigResponse(
                entity.getPrivacyTitle(),
                entity.getPrivacyVersion(),
                entity.getPrivacyContent() == null ? "" : entity.getPrivacyContent(),
                entity.getPrivacyContact(),
                entity.getPrivacyEffective(),
                Boolean.TRUE.equals(entity.getCustomPrivacy()));
        cachePrivacidad = respuesta;
        cachePrivacidadEn = System.currentTimeMillis();
        return respuesta;
    }

    private ErrorPageConfigResponse aRespuesta(ErrorPageConfig e) {
        return new ErrorPageConfigResponse(
                e.getError404Title(), e.getError404Message(), e.getError404Icon(), e.getError404Color(),
                e.getError403Title(), e.getError403Message(), e.getError403Icon(), e.getError403Color(),
                e.getError500Title(), e.getError500Message(), e.getError500Icon(), e.getError500Color(),
                e.getError503Title(), e.getError503Message(), e.getError503Icon(), e.getError503Color(),
                e.getErrorButtonText() == null || e.getErrorButtonText().isBlank()
                        ? "Volver al inicio" : e.getErrorButtonText(),
                !Boolean.FALSE.equals(e.getErrorShowCode()),
                !Boolean.FALSE.equals(e.getErrorShowPrivacy()));
    }

    /**
     * Icono: si no llega nada se conserva el que ya habia y, si tampoco hay,
     * el de por defecto. Nunca se deja vacio, porque la pantalla lo pinta.
     */
    private static String iconoONull(String recibido, String actual, String porDefecto) {
        if (recibido == null || recibido.isBlank()) {
            return (actual == null || actual.isBlank()) ? porDefecto : actual;
        }
        return recibido.trim();
    }

    /** Color hexadecimal o el de por defecto. La validacion de formato la hace el record. */
    private static String colorONull(String recibido, String porDefecto) {
        return (recibido == null || recibido.isBlank()) ? porDefecto : recibido.trim();
    }

    private static String vacioONull(String valor) {
        return (valor == null || valor.isBlank()) ? null : valor.trim();
    }
}