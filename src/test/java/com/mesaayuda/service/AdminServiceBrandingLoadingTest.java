package com.mesaayuda.service;

import com.mesaayuda.entity.BrandingConfig;
import com.mesaayuda.exception.BadRequestException;
import com.mesaayuda.repository.BrandingConfigRepository;
import com.mesaayuda.security.UrlSafetyValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La pantalla de carga del arranque se configura desde /admin/branding y se lee
 * desde GET /admin/branding, que es publico: el splash aparece antes de que
 * exista sesion.
 *
 * Por eso el servicio tiene que aguantar tres cosas: que el administrador
 * pueda vaciar la imagen o el icono (volver al icono del sistema sin tocar la
 * base a mano), que un valor de formulario inventado no se guarde tal cual
 * (la pantalla no sabria pintarlo) y que la imagen no pueda apuntar a un
 * recurso que no sea de marca.
 */
class AdminServiceBrandingLoadingTest {

      /**
       * Un PNG de 1x1 en blanco.
       *
       * <p>Hace falta una cabecera de firma real porque saveImage deduce el tipo
       * leyendo los primeros bytes del contenido, no el nombre del archivo. Un
       * fichero cualquiera ya no se acepta, y eso es justamente lo que protege la
       * zona publica de un SVG con script.
       */
      private static final byte[] PNG_MINIMO = new byte[] {
              (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
              0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
              0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
              0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, (byte) 0xC4,
              (byte) 0x89, 0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41,
              0x54, 0x78, (byte) 0x9C, 0x63, 0x00, 0x01, 0x00, 0x00,
              0x05, 0x00, 0x01, 0x0D, 0x0A, 0x2D, (byte) 0xB4, 0x00,
              0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, (byte) 0xAE,
              0x42, 0x60, (byte) 0x82
      };

      private AdminService service;
    private BrandingConfigRepository repository;
    private UrlSafetyValidator urlSafetyValidator;

    @BeforeEach
    void setUp() {
        service = new AdminService();
        repository = Mockito.mock(BrandingConfigRepository.class);
        urlSafetyValidator = Mockito.mock(UrlSafetyValidator.class);
        ReflectionTestUtils.setField(service, "brandingConfigRepository", repository);
        ReflectionTestUtils.setField(service, "urlSafetyValidator", urlSafetyValidator);

        BrandingConfig actual = new BrandingConfig();
        actual.setPrimaryColor("#062e86");
        actual.setSecondaryColor("#ec9811");
        Mockito.when(repository.findTopByOrderByIdBrandingAsc()).thenReturn(java.util.Optional.of(actual));
        // save() devuelve lo que le pasan, como el repositorio real.
        Mockito.when(repository.save(Mockito.any(BrandingConfig.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private BrandingConfig formulario(BrandingConfig c) {
        return c;
    }

    @Test
    @DisplayName("con imagen e icono propios se guardan tal cual")
    void guardaImagenYIcono() {
        BrandingConfig enviado = new BrandingConfig();
        enviado.setLoadingMode("IMAGE");
        enviado.setLoadingImageUrl("/uploads/publico/loading_1.png");
        enviado.setLoadingBackgroundColor("#0f172a");
        enviado.setLoadingTextColor("#ffffff");
        enviado.setLoadingTitle("Mesa de Servicios");
        enviado.setLoadingMessage("Preparando tu espacio");
        enviado.setLoadingAnimation("BARS");
        enviado.setLoadingProgress(true);
        enviado.setLoadingTheme("DARK");
        enviado.setLoadingMinDurationMs(2500);
        enviado.setLoadingSoloMarca(true);

        BrandingConfig guardado = service.updateBranding(enviado);

        assertThat(guardado.getLoadingMode()).isEqualTo("IMAGE");
        assertThat(guardado.getLoadingImageUrl()).isEqualTo("/uploads/publico/loading_1.png");
        assertThat(guardado.getLoadingBackgroundColor()).isEqualTo("#0f172a");
        assertThat(guardado.getLoadingTextColor()).isEqualTo("#ffffff");
        assertThat(guardado.getLoadingTitle()).isEqualTo("Mesa de Servicios");
        assertThat(guardado.getLoadingMessage()).isEqualTo("Preparando tu espacio");
        assertThat(guardado.getLoadingAnimation()).isEqualTo("BARS");
        assertThat(guardado.getLoadingProgress()).isTrue();
        assertThat(guardado.getLoadingTheme()).isEqualTo("DARK");
        assertThat(guardado.getLoadingMinDurationMs()).isEqualTo(2500);
        assertThat(guardado.getLoadingSoloMarca()).isTrue();
    }

    @Test
    @DisplayName("vaciar la imagen deja el modo AUTO para volver al icono del sistema")
    void vaciarImagenVuelveAlIconoDelSistema() {
        BrandingConfig enviado = new BrandingConfig();
        enviado.setLoadingMode("AUTO");
        enviado.setLoadingImageUrl("");
        enviado.setLoadingIcon("");

        BrandingConfig guardado = service.updateBranding(enviado);

        assertThat(guardado.getLoadingMode()).isEqualTo("AUTO");
        assertThat(guardado.getLoadingImageUrl()).isNull();
        assertThat(guardado.getLoadingIcon()).isNull();
    }

    @Test
    @DisplayName("un modo o una animacion inventada caen en el valor por defecto")
    void valoresFueraDeListaCaenEnElPredeterminado() {
        BrandingConfig enviado = new BrandingConfig();
        enviado.setLoadingMode("video");
        enviado.setLoadingAnimation("BOUNCING");
        enviado.setLoadingLogoEffect("EXPLOSION");
        enviado.setLoadingTheme("NEON");

        BrandingConfig guardado = service.updateBranding(enviado);

        assertThat(guardado.getLoadingMode()).isEqualTo("AUTO");
        assertThat(guardado.getLoadingAnimation()).isEqualTo("SPINNER");
        assertThat(guardado.getLoadingLogoEffect()).isEqualTo("NONE");
        assertThat(guardado.getLoadingTheme()).isEqualTo("LIGHT");
    }

    @Test
    @DisplayName("el efecto del logo y el indicador se guardan por separado")
    void efectoDelLogoYIndicadorSonIndependientes() {
        BrandingConfig enviado = new BrandingConfig();
        enviado.setLoadingAnimation("BARS");
        enviado.setLoadingLogoEffect("FADE");

        BrandingConfig guardado = service.updateBranding(enviado);

        assertThat(guardado.getLoadingAnimation()).isEqualTo("BARS");
        assertThat(guardado.getLoadingLogoEffect()).isEqualTo("FADE");
    }

    @Test
    @DisplayName("el modo y la animacion se aceptan en minusculas")
    void normalizaMinusculas() {
        BrandingConfig enviado = new BrandingConfig();
        enviado.setLoadingMode(" icon ");
        enviado.setLoadingAnimation("dots");

        BrandingConfig guardado = service.updateBranding(enviado);

        assertThat(guardado.getLoadingMode()).isEqualTo("ICON");
        assertThat(guardado.getLoadingAnimation()).isEqualTo("DOTS");
    }

    @Test
    @DisplayName("la duracion minima se acota para que el splash no se quede pegado")
    void acotaDuracionMinima() {
        BrandingConfig negativa = new BrandingConfig();
        negativa.setLoadingMinDurationMs(-900);
        assertThat(service.updateBranding(negativa).getLoadingMinDurationMs()).isZero();

        BrandingConfig enorme = new BrandingConfig();
        enorme.setLoadingMinDurationMs(999999);
        assertThat(service.updateBranding(enorme).getLoadingMinDurationMs()).isEqualTo(10000);
    }

    @Test
    @DisplayName("una duracion minima de cero se respeta: el administrador puede quitarla")
    void duracionCeroSeRespeta() {
        Mockito.when(repository.findTopByOrderByIdBrandingAsc()).thenAnswer(inv ->
                java.util.Optional.of(new BrandingConfig()));

        BrandingConfig enviado = new BrandingConfig();
        enviado.setLoadingMinDurationMs(0);

        assertThat(service.updateBranding(enviado).getLoadingMinDurationMs()).isZero();
    }

    @Test
    @DisplayName("una marca sin configurar arranca con los valores por defecto del splash")
    void defaultsDeLaMarca() {
        Mockito.when(repository.findTopByOrderByIdBrandingAsc())
                .thenReturn(java.util.Optional.empty());

        BrandingConfig config = service.getBranding();

        // AUTO + icono del sistema y fondo en el color primario es lo que la
        // pantalla de carga necesita para no depender de que se configure nada.
        assertThat(config.getLoadingMode()).isEqualTo("AUTO");
        assertThat(config.getLoadingImageUrl()).isNull();
        assertThat(config.getLoadingBackgroundColor()).isNull();
        assertThat(config.getLoadingAnimation()).isEqualTo("SPINNER");
        assertThat(config.getLoadingLogoEffect()).isEqualTo("NONE");
        assertThat(config.getLoadingTheme()).isEqualTo("LIGHT");
        assertThat(config.getLoadingMinDurationMs()).isEqualTo(1200);
        assertThat(config.getLoadingSoloMarca()).isFalse();
        assertThat(config.getLoadingProgress()).isFalse();
        assertThat(config.getPrimaryColor()).isEqualTo("#062e86");
    }

@Test
      @DisplayName("subir una imagen del splash la deja en modo IMAGE y solo marca")
      void subirImagenActivaSoloMarca() throws Exception {
          MultipartFile archivo = Mockito.mock(MultipartFile.class);
          // El contenido tiene que ser una imagen de verdad: saveImage lee la firma
          // del archivo y no su nombre. Antes el test mandaba {1,2,3} y pasaba solo
          // porque no se comprobaba nada; ahora se rechaza, que es lo correcto.
          byte[] png = PNG_MINIMO;
          Mockito.when(archivo.isEmpty()).thenReturn(false);
          Mockito.when(archivo.getSize()).thenReturn((long) png.length);
          Mockito.when(archivo.getOriginalFilename()).thenReturn("logo.png");
          Mockito.when(archivo.getBytes()).thenReturn(png);
          ReflectionTestUtils.setField(service, "uploadDir",
                  Files.createTempDirectory("uploads-loading").toString());

          Map<String, String> res = service.saveLoadingImage(archivo);

          assertThat(res.get("url")).startsWith("/uploads/publico/loading_");
          // La extension la decide el contenido, no el nombre que envio el
          // administrador: aqui coinciden, pero el criterio es otro.
          assertThat(res.get("url")).endsWith(".png");

        // Es lo que pidió el administrador: su imagen, sin un "Cargando..."
        // montado encima.
        ArgumentCaptor<BrandingConfig> guardado = ArgumentCaptor.forClass(BrandingConfig.class);
        Mockito.verify(repository).save(guardado.capture());
        assertThat(guardado.getValue().getLoadingMode()).isEqualTo("IMAGE");
        assertThat(guardado.getValue().getLoadingSoloMarca()).isTrue();
        assertThat(guardado.getValue().getLoadingImageUrl()).isEqualTo(res.get("url"));
    }

    @Test
    @DisplayName("el tamaño del logo del splash se acota a un rango usable")
    void acotaElTamanoDeLaImagen() {
        BrandingConfig diminuta = new BrandingConfig();
        diminuta.setLoadingImageSize(4);
        assertThat(service.updateBranding(diminuta).getLoadingImageSize()).isEqualTo(48);

        BrandingConfig enorme = new BrandingConfig();
        enorme.setLoadingImageSize(5000);
        assertThat(service.updateBranding(enorme).getLoadingImageSize()).isEqualTo(256);

        BrandingConfig normal = new BrandingConfig();
        normal.setLoadingImageSize(160);
        assertThat(service.updateBranding(normal).getLoadingImageSize()).isEqualTo(160);
    }

    @Test
    @DisplayName("volver a guardar lo que devuelve GET no choca con la comprobacion de red")
    void idaYVueltaDeLasUrlsDeMarca() {
        // GET /admin/branding devuelve las imagenes como URLs absolutas y el
        // formulario las manda de vuelta al guardar. Si se guardaran tal cual,
        // el despliegue se quedaria apuntando al host viejo y el propio guardado
        // se rechazaria, porque localhost no pasa la comprobacion de red interna.
        BrandingConfig devolvidoPorGet = new BrandingConfig();
        devolvidoPorGet.setBackgroundUrl("http://localhost:8080/api/uploads/publico/fondo.jpg");
        devolvidoPorGet.setLogoLoginUrl("http://localhost:8080/api/uploads/publico/logo_login.png");
        devolvidoPorGet.setLoadingImageUrl("http://localhost:8080/api/uploads/publico/loading.svg");
        devolvidoPorGet.setPrimaryColor("#062e86");

        BrandingConfig guardado = service.updateBranding(devolvidoPorGet);

        assertThat(guardado.getBackgroundUrl()).isEqualTo("/uploads/publico/fondo.jpg");
        assertThat(guardado.getLogoLoginUrl()).isEqualTo("/uploads/publico/logo_login.png");
        assertThat(guardado.getLoadingImageUrl()).isEqualTo("/uploads/publico/loading.svg");
        // Ninguna comprobacion de red externa llega a dispararse.
        Mockito.verifyNoInteractions(urlSafetyValidator);
    }

    @Test
    @DisplayName("una imagen externa de verdad no se reescribe y sigue validandose")
    void unaUrlExternaSeValidaYNoSeReescribe() {
        BrandingConfig externo = new BrandingConfig();
        externo.setBackgroundUrl("https:// intranet.interno/fondo.jpg");
        externo.setLogoUrl("http://10.0.0.5/logo.png");

        BrandingConfig guardado = service.updateBranding(externo);

        // La normalizacion solo toca lo que cuelga de /uploads/publico/: una
        // direccion externa se guarda tal cual y se comprueba contra la red interna.
        assertThat(guardado.getBackgroundUrl()).isEqualTo("https:// intranet.interno/fondo.jpg");
        Mockito.verify(urlSafetyValidator).exigirUrlPublica(
                Mockito.eq("https:// intranet.interno/fondo.jpg"), Mockito.eq("El fondo"));
        Mockito.verify(urlSafetyValidator).exigirUrlPublica(
                Mockito.eq("http://10.0.0.5/logo.png"), Mockito.eq("El logo"));
    }

    @Test
    @DisplayName("una url externa con marca de tiempo conserva la ruta y la descarta")
    void normalizaConservandoLaRutaYSinQuery() {
        BrandingConfig conQuery = new BrandingConfig();
        conQuery.setLoadingImageUrl("http://localhost:8080/api/uploads/publico/loading.png?v=3");

        assertThat(service.updateBranding(conQuery).getLoadingImageUrl())
                .isEqualTo("/uploads/publico/loading.png");
    }

    @Test
    @DisplayName("la imagen del splash se valida como recurso de marca")
    void validaUrlDeLaImagenDelSplash() {
        BrandingConfig anexo = new BrandingConfig();
        anexo.setLoadingImageUrl("/uploads/tickets/adjunto.pdf");
        assertThatThrownBy(() -> service.updateBranding(anexo))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("pantalla de carga");

        // Una ruta de la zona pública si se acepta.
        BrandingConfig propia = new BrandingConfig();
        propia.setLoadingImageUrl("/uploads/publico/loading_9.png");
        assertThat(service.updateBranding(propia).getLoadingImageUrl())
                .isEqualTo("/uploads/publico/loading_9.png");

        // Y una dirección externa pasa por el validador de URLs públicas.
        BrandingConfig externa = new BrandingConfig();
        externa.setLoadingImageUrl("https://ejemplo.gob.ec/loading.png");
        service.updateBranding(externa);
        Mockito.verify(urlSafetyValidator).exigirUrlPublica(
                Mockito.eq("https://ejemplo.gob.ec/loading.png"), Mockito.anyString());
    }
}
