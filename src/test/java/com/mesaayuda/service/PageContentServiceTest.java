package com.mesaayuda.service;

import static com.mesaayuda.IdsPrueba.id;
import com.mesaayuda.dto.ErrorPageConfigRequest;
import com.mesaayuda.dto.ErrorPageConfigResponse;
import com.mesaayuda.dto.PrivacyConfigRequest;
import com.mesaayuda.dto.PrivacyConfigResponse;
import com.mesaayuda.entity.ErrorPageConfig;
import com.mesaayuda.entity.PrivacyConfig;
import com.mesaayuda.repository.ErrorPageConfigRepository;
import com.mesaayuda.repository.PrivacyConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Textos de error y politica de privacidad editables.
 *
 * Lo que se comprueba aqui es lo que en produccion daria problemas:
 *
 * - La lectura publica nunca debe lanzar. La pantalla de un 500 pide su propio
 *   texto; si esa consulta falla, el usuario ve una pagina en blanco, que es
 *   peor que ver un texto generico.
 * - Guardar la politica tiene que activar la marca de edicion, porque de eso
 *   depende que el seed del repositorio no la pise en el siguiente reinicio.
 * - Los campos de icono y color no pueden quedar vacios: la pantalla los pinta.
 */
@ExtendWith(MockitoExtension.class)
class PageContentServiceTest {

    @Mock private ErrorPageConfigRepository errorPageConfigRepository;
    @Mock private PrivacyConfigRepository privacyConfigRepository;

    @InjectMocks private PageContentService service;

    @BeforeEach
    void setUp() {
        // Cada test arranca sin copia en memoria: si no, el cache de uno se
        // filtraria al siguiente y las pruebas serian dependientes entre si.
        service.invalidarCache();
    }

    private static ErrorPageConfigRequest textos(String titulo) {
        return new ErrorPageConfigRequest(
                titulo, "mensaje 404", "fa-solid fa-compass", "#64748b",
                "titulo 403", "mensaje 403", "fa-solid fa-lock", "#d97706",
                "titulo 500", "mensaje 500", "fa-solid fa-bug", "#dc2626",
                "titulo 503", "mensaje 503", "fa-solid fa-tools", "#2563eb");
    }

    // ============================ ERRORES ============================

    @Test
    @DisplayName("si la tabla está vacía se devuelven los textos por defecto, no un null")
    void textosPorDefectoSiNoHayFila() {
        when(errorPageConfigRepository.findTopByOrderByIdErrorPageAsc()).thenReturn(Optional.empty());

        ErrorPageConfigResponse r = service.textosDeError();

        assertEquals("Página no encontrada", r.error404Title());
        assertEquals("fa-solid fa-compass", r.error404Icon());
        assertEquals("Acceso no autorizado", r.error403Title());
    }

    @Test
    @DisplayName("si la lectura falla se devuelven los textos por defecto y no se propaga el error")
    void textoPorDefectoSiLaBaseFalla() {
        when(errorPageConfigRepository.findTopByOrderByIdErrorPageAsc())
                .thenThrow(new RuntimeException("la base no responde"));

        ErrorPageConfigResponse r = service.textosDeError();

        // Lo importante es que no salte: es la pantalla de error del servidor.
        assertEquals("Error interno del servidor", r.error500Title());
        assertEquals("Servicio no disponible", r.error503Title());
    }

    @Test
    @DisplayName("guardar devuelve lo guardado y deja la fila lista para proximas lecturas")
    void guardaLosTextos() {
        when(errorPageConfigRepository.findTopByOrderByIdErrorPageAsc()).thenReturn(Optional.empty());
        when(errorPageConfigRepository.save(any(ErrorPageConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        ErrorPageConfigResponse r = service.guardarTextosDeError(textos("No existe"), id(7));

        assertEquals("No existe", r.error404Title());
        assertEquals("mensaje 500", r.error500Message());
    }

    @Test
    @DisplayName("un icono vacio conserva el anterior y no deja la pantalla sin icono")
    void iconoVacioConservaElAnterior() {
        ErrorPageConfig actual = new ErrorPageConfig();
        actual.setError404Icon("fa-solid fa-house");
        when(errorPageConfigRepository.findTopByOrderByIdErrorPageAsc()).thenReturn(Optional.of(actual));
        when(errorPageConfigRepository.save(any(ErrorPageConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        ErrorPageConfigRequest sinIcono = new ErrorPageConfigRequest(
                "titulo", "mensaje", "   ", "#64748b",
                "t403", "m403", null, "#d97706",
                "t500", "m500", null, "#dc2626",
                "t503", "m503", "", "#2563eb");

        ErrorPageConfigResponse r = service.guardarTextosDeError(sinIcono, id(7));

        assertEquals("fa-solid fa-house", r.error404Icon());
        // Sin valor previo tampoco puede quedar vacio: la entidad nace con icono
        // por defecto, y si tampoco lo tuviera caeria al del parametro.
        assertEquals("fa-solid fa-lock", r.error403Icon());
        assertEquals("fa-solid fa-triangle-exclamation", r.error500Icon());
        assertEquals("fa-solid fa-screwdriver-wrench", r.error503Icon());
    }

    @Test
    @DisplayName("un estado sin icono previo cae al icono por defecto del estado")
    void iconoSinPrevioUsaElPorDefectoDelEstado() {
        // Entidad totalmente vacia: ningun icono sembrado en ella.
        ErrorPageConfig vacia = new ErrorPageConfig();
        vacia.setError404Icon(null);
        vacia.setError500Icon(null);
        when(errorPageConfigRepository.findTopByOrderByIdErrorPageAsc()).thenReturn(Optional.of(vacia));
        when(errorPageConfigRepository.save(any(ErrorPageConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        // Iconos a null, para forzar el camino del valor por defecto.
        ErrorPageConfigResponse r = service.guardarTextosDeError(new ErrorPageConfigRequest(
                "t", "m", null, "#64748b",
                "t403", "m403", null, "#d97706",
                "t500", "m500", null, "#dc2626",
                "t503", "m503", null, "#2563eb"), id(7));

        assertEquals("fa-solid fa-compass", r.error404Icon());
        assertEquals("fa-solid fa-triangle-exclamation", r.error500Icon());
    }

    @Test
    @DisplayName("un color vacio cae al color por defecto del estado")
    void colorVacioUsaElPorDefecto() {
        when(errorPageConfigRepository.findTopByOrderByIdErrorPageAsc()).thenReturn(Optional.empty());
        when(errorPageConfigRepository.save(any(ErrorPageConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        ErrorPageConfigRequest sinColores = new ErrorPageConfigRequest(
                "t404", "m404", "fa-solid fa-compass", null,
                "t403", "m403", "fa-solid fa-lock", "  ",
                "t500", "m500", "fa-solid fa-bug", "",
                "t503", "m503", "fa-solid fa-tools", null);

        ErrorPageConfigResponse r = service.guardarTextosDeError(sinColores, id(7));

        assertEquals("#64748b", r.error404Color());
        assertEquals("#d97706", r.error403Color());
        assertEquals("#dc2626", r.error500Color());
        assertEquals("#2563eb", r.error503Color());
    }

    @Test
    @DisplayName("el titulo se guarda sin espacios sobrantes")
    void recortaLosTextos() {
        when(errorPageConfigRepository.findTopByOrderByIdErrorPageAsc()).thenReturn(Optional.empty());
        ArgumentCaptor<ErrorPageConfig> cap = ArgumentCaptor.forClass(ErrorPageConfig.class);
        when(errorPageConfigRepository.save(cap.capture())).thenAnswer(inv -> inv.getArgument(0));

        service.guardarTextosDeError(textos("   No existe   "), id(7));

        assertEquals("No existe", cap.getValue().getError404Title());
    }

    // ============================ PRIVACIDAD ============================

    @Test
    @DisplayName("guardar la politica activa la marca de edicion, para que el seed no la pise")
    void guardarActivaLaMarcaDeEdicion() {
        when(privacyConfigRepository.findTopByOrderByIdPrivacyConfigAsc()).thenReturn(Optional.empty());
        ArgumentCaptor<PrivacyConfig> cap = ArgumentCaptor.forClass(PrivacyConfig.class);
        when(privacyConfigRepository.save(cap.capture())).thenAnswer(inv -> inv.getArgument(0));

        PrivacyConfigResponse r = service.guardarPoliticaDePrivacidad(
                new PrivacyConfigRequest("Politica", "2.0", "# texto", "datos@coac.gob.ec", "enero 2026"), id(7));

        assertTrue(r.edited());
        assertTrue(cap.getValue().getCustomPrivacy());
        assertEquals("2.0", r.privacyVersion());
        assertEquals("# texto", r.privacyContent());
    }

    @Test
    @DisplayName("una politica sin editar se marca como no editada")
    void borradorSeMarcaComoNoEditado() {
        PrivacyConfig borrador = new PrivacyConfig();
        borrador.setCustomPrivacy(false);
        borrador.setPrivacyContent("borrador");
        when(privacyConfigRepository.findTopByOrderByIdPrivacyConfigAsc()).thenReturn(Optional.of(borrador));

        assertFalse(service.politicaDePrivacidad().edited());
    }

    @Test
    @DisplayName("un cuerpo vacio se guarda como cadena vacia, no como null")
    void cuerpoVacioNoEsNull() {
        when(privacyConfigRepository.findTopByOrderByIdPrivacyConfigAsc()).thenReturn(Optional.empty());
        ArgumentCaptor<PrivacyConfig> cap = ArgumentCaptor.forClass(PrivacyConfig.class);
        when(privacyConfigRepository.save(cap.capture())).thenAnswer(inv -> inv.getArgument(0));

        PrivacyConfigResponse r = service.guardarPoliticaDePrivacidad(
                new PrivacyConfigRequest("Politica", "1.0", null, null, null), id(7));

        assertEquals("", r.privacyContent());
        assertEquals("", cap.getValue().getPrivacyContent());
    }

    @Test
    @DisplayName("si la lectura de la politica falla se devuelven los valores por defecto")
    void privacidadPorDefectoSiLaBaseFalla() {
        when(privacyConfigRepository.findTopByOrderByIdPrivacyConfigAsc())
                .thenThrow(new RuntimeException("sin conexion"));

        PrivacyConfigResponse r = service.politicaDePrivacidad();

        assertEquals("Politica de privacidad", r.privacyTitle());
        assertEquals("", r.privacyContent());
    }

    @Test
    @DisplayName("guardar invalida la copia en memoria: la lectura siguiente trae lo nuevo")
    void guardarInvalidaLaCache() {
        when(privacyConfigRepository.findTopByOrderByIdPrivacyConfigAsc()).thenReturn(Optional.empty());
        // Primera lectura: entra en cache.
        service.politicaDePrivacidad();
        when(privacyConfigRepository.save(any(PrivacyConfig.class))).thenAnswer(inv -> inv.getArgument(0));
        service.guardarPoliticaDePrivacidad(
                new PrivacyConfigRequest("Titulo nuevo", "9.9", "cuerpo", null, null), id(7));

        assertEquals("Titulo nuevo", service.politicaDePrivacidad().privacyTitle());
    }
}