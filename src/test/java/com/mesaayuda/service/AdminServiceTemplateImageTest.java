package com.mesaayuda.service;

import java.util.UUID;
import static com.mesaayuda.IdsPrueba.id;
import com.mesaayuda.entity.NotificationTemplate;
import com.mesaayuda.entity.NotificationTemplateImage;
import com.mesaayuda.exception.BadRequestException;
import com.mesaayuda.repository.NotificationTemplateImageRepository;
import com.mesaayuda.repository.NotificationTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Las imagenes de las plantillas se guardan en la base y no en disco, para que
 * el despliegue no dependa de una carpeta de archivos. Eso deja dos cosas que
 * hay que vigilar: que lo que se sube sea de verdad una imagen (el nombre del
 * archivo lo elige quien lo sube) y que la ruta que se guarda no sea adivinable,
 * porque el correo la carga sin sesion.
 */
class AdminServiceTemplateImageTest {

    private AdminService service;
    private NotificationTemplateImageRepository imageRepository;
    private NotificationTemplateRepository templateRepository;

    /** Cabecera minima de un PNG de 1x1. */
    private static final byte[] PNG = new byte[] {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52
    };

    @BeforeEach
    void setUp() {
        service = new AdminService();
        imageRepository = Mockito.mock(NotificationTemplateImageRepository.class);
        templateRepository = Mockito.mock(NotificationTemplateRepository.class);
        ReflectionTestUtils.setField(service, "templateImageRepository", imageRepository);
        ReflectionTestUtils.setField(service, "templateRepository", templateRepository);
    }

    private MultipartFile archivo(byte[] contenido, String nombre) {
        MultipartFile file = Mockito.mock(MultipartFile.class);
        try {
            Mockito.when(file.getBytes()).thenReturn(contenido);
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
        Mockito.when(file.isEmpty()).thenReturn(contenido == null || contenido.length == 0);
        Mockito.when(file.getSize()).thenReturn((long) (contenido == null ? 0 : contenido.length));
        Mockito.when(file.getOriginalFilename()).thenReturn(nombre);
        return file;
    }

    @Test
    @DisplayName("una imagen valida se guarda en la base con un token aleatorio")
    void guardaLaImagenEnLaBase() {
        Mockito.when(imageRepository.save(Mockito.any(NotificationTemplateImage.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Map<String, String> res = service.saveTemplateImage(archivo(PNG, "aviso.png"));

        // La ruta lleva token, no el id de la fila: el correo la pide sin sesion.
        assertThat(res.get("url")).matches("/templates/image/[0-9a-f]{32}");

        ArgumentCaptor<NotificationTemplateImage> guardada =
                ArgumentCaptor.forClass(NotificationTemplateImage.class);
        Mockito.verify(imageRepository).save(guardada.capture());
        assertThat(guardada.getValue().getMimeTypeImage()).isEqualTo("image/png");
        assertThat(guardada.getValue().getDataImage()).isEqualTo(PNG);
        assertThat(guardada.getValue().getSizeBytesImage()).isEqualTo(PNG.length);
        // Todavia no pertenece a ninguna plantilla: se vincula al guardarla.
        assertThat(guardada.getValue().getIdTemplate()).isNull();
    }

    @Test
    @DisplayName("cada imagen recibe un token distinto")
    void losTokensNoSeRepiten() {
        Mockito.when(imageRepository.save(Mockito.any(NotificationTemplateImage.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        String a = service.saveTemplateImage(archivo(PNG, "a.png")).get("url");
        String b = service.saveTemplateImage(archivo(PNG, "b.png")).get("url");

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("un archivo que no es imagen se rechaza aunque se llame .png")
    void rechazaLoQueNoEsImagen() {
        byte[] html = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> service.saveTemplateImage(archivo(html, "logo.png")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("PNG");

        Mockito.verify(imageRepository, Mockito.never()).save(Mockito.any());
    }

    @Test
    @DisplayName("se aceptan JPG, GIF y WEBP, y SVG no")
    void tiposAdmitidos() {
        // GIF89a: la firma es "GIF89a", asi que el byte 8 debe ser '9' (0x39).
        byte[] jpg = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0, 0, 0 };
        // Firmas reales: el GIF lleva la version en el byte 4 ("GIF89a"), y el
        // WebP es "RIFF" + 4 bytes de tamaño + "WEBP", que cae en los bytes 8..11.
        byte[] gif = { 0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0, 0, 0, 0, 0, 0 };
        byte[] gif87 = { 0x47, 0x49, 0x46, 0x38, 0x37, 0x61, 0, 0, 0, 0, 0, 0 };
        byte[] webp = { 0x52, 0x49, 0x46, 0x46, 0x24, 0x00, 0x00, 0x00,
                        0x57, 0x45, 0x42, 0x50 };

        assertThat(tipoGuardado(jpg, "a.jpg")).isEqualTo("image/jpeg");
        assertThat(tipoGuardado(gif, "a.gif")).isEqualTo("image/gif");
        assertThat(tipoGuardado(gif87, "a87.gif")).isEqualTo("image/gif");
        assertThat(tipoGuardado(webp, "a.webp")).isEqualTo("image/webp");

        // SVG es un documento que puede llevar script y se sirve en ruta publica.
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>"
                .getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> service.saveTemplateImage(archivo(svg, "a.svg")))
                .isInstanceOf(BadRequestException.class);
    }

    private String tipoGuardado(byte[] contenido, String nombre) {
        ArgumentCaptor<NotificationTemplateImage> cap =
                ArgumentCaptor.forClass(NotificationTemplateImage.class);
        Mockito.reset(imageRepository);
        Mockito.when(imageRepository.save(Mockito.any(NotificationTemplateImage.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        service.saveTemplateImage(archivo(contenido, nombre));
        Mockito.verify(imageRepository).save(cap.capture());
        return cap.getValue().getMimeTypeImage();
    }

    @Test
    @DisplayName("una imagen demasiado grande no llega a la base")
    void rechazaElTamanoExcesivo() {
        MultipartFile grande = Mockito.mock(MultipartFile.class);
        Mockito.when(grande.isEmpty()).thenReturn(false);
        Mockito.when(grande.getSize()).thenReturn(3L * 1024 * 1024);

        assertThatThrownBy(() -> service.saveTemplateImage(grande))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("2 MB");

        Mockito.verify(imageRepository, Mockito.never()).save(Mockito.any());
    }

    @Test
    @DisplayName("solo se sirven imagenes con un token con forma de token")
    void buscarPorTokenRechazaTokensInvalidos() {
        assertThat(service.getTemplateImage("../../uploads/publico/logo.png")).isNull();
        assertThat(service.getTemplateImage("1")).isNull();
        assertThat(service.getTemplateImage("ZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZ")).isNull();
        assertThat(service.getTemplateImage(null)).isNull();

        // Con el token en el formato correcto, se consulta el repositorio.
        Mockito.verify(imageRepository, Mockito.never()).findByTokenImage(Mockito.any());
        Mockito.when(imageRepository.findByTokenImage("0123456789abcdef0123456789abcdef"))
                .thenReturn(Optional.of(new NotificationTemplateImage()));
        assertThat(service.getTemplateImage("0123456789abcdef0123456789abcdef")).isNotNull();
    }

    @Test
    @DisplayName("al borrar la plantilla se borra tambien su imagen")
    void borrarPlantillaBorraSuImagen() {
        service.deleteTemplate(id(7));

        Mockito.verify(imageRepository).deleteByIdTemplate(id(7));
        Mockito.verify(templateRepository).deleteById(id(7));
    }

    @Test
    @DisplayName("la ruta de la imagen se acepta al guardar la plantilla")
    void laRutaDeLaBaseEsUnaUrlValida() {
        String ruta = "/templates/image/0123456789abcdef0123456789abcdef";
        // saveTemplate con id busca la plantilla por id, no por nombre.
        var plantilla = plantillaConId(id(1));
        Mockito.when(templateRepository.findById(id(1))).thenReturn(Optional.of(plantilla));
        Mockito.when(templateRepository.save(Mockito.any())).thenAnswer(inv -> inv.getArgument(0));
        Mockito.when(imageRepository.findByTokenImage("0123456789abcdef0123456789abcdef"))
                .thenReturn(Optional.of(new NotificationTemplateImage()));
        Mockito.when(imageRepository.save(Mockito.any(NotificationTemplateImage.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var guardada = service.saveTemplate(id(1), "TICKET_CREADO", "TICKET", "Asunto",
                "<p>[imagen]</p>", true, null, null, null, null, ruta);

        assertThat(guardada.getImageUrl()).isEqualTo(ruta);
    }

    @Test
    @DisplayName("una ruta con forma de token pero que no existe se rechaza")
    void unaRutaDeImagenInexistenteSeRechaza() {
        Mockito.when(templateRepository.findById(id(1)))
                .thenReturn(Optional.of(plantillaConId(id(1))));
        Mockito.when(templateRepository.save(Mockito.any())).thenAnswer(inv -> inv.getArgument(0));
        Mockito.when(imageRepository.findByTokenImage(Mockito.any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.saveTemplate(id(1), "TICKET_CREADO", "TICKET", "Asunto",
                "<p>cuerpo</p>", true, null, null, null, null,
                "/templates/image/0123456789abcdef0123456789abcdef"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("no existe");
    }

    /**
 * El endpoint de subida devuelve la URL absoluta (http://host/api/templates/
 * image/token) y el formulario la manda de vuelta al guardar. Si el guardado
 * solo entendiera la ruta relativa, el alta de la plantilla fallaria con "la
 * imagen indicada no existe" aunque la imagen estubiera subida de verdad.
 */
@Test
@DisplayName("guardar acepta la URL absoluta que devolvio la subida")
void aceptaLaUrlAbsolutaDeLaSubida() {
String token = "0123456789abcdef0123456789abcdef";
Mockito.when(templateRepository.findById(id(1)))
.thenReturn(Optional.of(plantillaConId(id(1))));
Mockito.when(templateRepository.save(Mockito.any())).thenAnswer(inv -> inv.getArgument(0));
Mockito.when(imageRepository.findByTokenImage(token))
.thenReturn(Optional.of(new NotificationTemplateImage()));
Mockito.when(imageRepository.save(Mockito.any(NotificationTemplateImage.class)))
.thenAnswer(inv -> inv.getArgument(0));

var guardada = service.saveTemplate(id(1), "TICKET_CREADO", "TICKET", "Asunto",
"<p>cuerpo</p>", true, null, null, null, null,
"http://localhost:8080/api/templates/image/" + token);

assertThat(guardada.getImageUrl()).isEqualTo("http://localhost:8080/api/templates/image/" + token);
// Y la imagen queda vinculada a la plantilla.
ArgumentCaptor<NotificationTemplateImage> cap =
ArgumentCaptor.forClass(NotificationTemplateImage.class);
Mockito.verify(imageRepository, Mockito.atLeastOnce()).save(cap.capture());
assertThat(cap.getValue().getIdTemplate()).isEqualTo(id(1));
}

/** Una ruta con el context-path delante también vale. */
@Test
@DisplayName("guardar acepta la ruta con el context-path de la API")
void aceptaLaRutaConContextPath() {
String token = "0123456789abcdef0123456789abcdef";
Mockito.when(templateRepository.findById(id(1)))
.thenReturn(Optional.of(plantillaConId(id(1))));
Mockito.when(templateRepository.save(Mockito.any())).thenAnswer(inv -> inv.getArgument(0));
Mockito.when(imageRepository.findByTokenImage(token))
.thenReturn(Optional.of(new NotificationTemplateImage()));
Mockito.when(imageRepository.save(Mockito.any(NotificationTemplateImage.class)))
.thenAnswer(inv -> inv.getArgument(0));

service.saveTemplate(id(1), "TICKET_CREADO", "TICKET", "Asunto", "<p>cuerpo</p>",
true, null, null, null, null, "/api/templates/image/" + token);

Mockito.verify(imageRepository).findByTokenImage(token);
}

/** Con query string también: el token es lo que va justo antes. */
@Test
@DisplayName("el token se saca aunque la ruta traiga parametros")
void aceptaParametrosEnLaRuta() {
String token = "0123456789abcdef0123456789abcdef";
Mockito.when(templateRepository.findById(id(1)))
.thenReturn(Optional.of(plantillaConId(id(1))));
Mockito.when(templateRepository.save(Mockito.any())).thenAnswer(inv -> inv.getArgument(0));
Mockito.when(imageRepository.findByTokenImage(token))
.thenReturn(Optional.of(new NotificationTemplateImage()));
Mockito.when(imageRepository.save(Mockito.any(NotificationTemplateImage.class))).thenAnswer(inv -> inv.getArgument(0));

service.saveTemplate(id(1), "TICKET_CREADO", "TICKET", "Asunto", "<p>cuerpo</p>",
true, null, null, null, null,
"http://localhost:8080/api/templates/image/" + token + "?v=3");

Mockito.verify(imageRepository).findByTokenImage(token);
}

/** Una imagen que ya es de otra plantilla no se puede adueñar. */
@Test
@DisplayName("no se puede tomar la imagen de otra plantilla")
void noSeRobaLaImagenDeOtraPlantilla() {
String token = "0123456789abcdef0123456789abcdef";
NotificationTemplateImage ajena = new NotificationTemplateImage();
ajena.setIdTemplate(id(99));
Mockito.when(templateRepository.findById(id(1)))
.thenReturn(Optional.of(plantillaConId(id(1))));
Mockito.when(templateRepository.save(Mockito.any())).thenAnswer(inv -> inv.getArgument(0));
Mockito.when(imageRepository.findByTokenImage(token)).thenReturn(Optional.of(ajena));

assertThatThrownBy(() -> service.saveTemplate(id(1), "TICKET_CREADO", "TICKET", "Asunto",
"<p>cuerpo</p>", true, null, null, null, null,
"/templates/image/" + token))
.isInstanceOf(BadRequestException.class)
.hasMessageContaining("otra plantilla");
}

/**
 * Los scripts de arranque (data.sql) se re-ejecutan en cada inicio y reescriben
 * el cuerpo y el destinatario de las plantillas semilla. Guardar una plantilla
 * desde la pantalla de administración tiene que marcarla como personalizada para
 * que esos scripts la respeten: si no, cada reinicio devolvía al admin el texto
 * que él acababa de escribir.
 */
/**
 * Un alta o una edicion desde la pantalla de plantillas siempre marca la
 * plantilla. Solo la semilla queda sin marcar, y es justo la que data.sql puede
 * seguir actualizando en cada arranque.
 */
@Test
@DisplayName("guardar una plantilla por id la marca como personalizada")
void guardarMarcaLaPlantillaComoPersonalizada() {
Mockito.when(templateRepository.findById(id(1)))
.thenReturn(Optional.of(plantillaConId(id(1))));
Mockito.when(templateRepository.save(Mockito.any())).thenAnswer(inv -> inv.getArgument(0));

var guardada = service.saveTemplate(id(1), "TICKET_CREADO", "TICKET", "Asunto",
"<p>hola</p>", true, "AGENTE", "a@b.ec", null, null, null);

assertThat(guardada.getCustomTemplate()).isTrue();
}

@Test
@DisplayName("editar una plantilla tambien la marca como personalizada")
void editarMarcaLaPlantillaComoPersonalizada() {
Mockito.when(templateRepository.findById(id(1)))
.thenReturn(Optional.of(plantillaConId(id(1))));
Mockito.when(templateRepository.save(Mockito.any())).thenAnswer(inv -> inv.getArgument(0));

var guardada = service.updateTemplate(id(1), "Asunto", "<p>hola</p>", true,
"AMBOS", null, null, null, null);

assertThat(guardada.getCustomTemplate()).isTrue();
}

/**
 * data.sql solo actualiza las plantillas con custom_template = FALSE. Este test
 * fija el otro lado de la garantía: que la columna exista en el esquema y que la
 * entidad la exponga, porque si el marcador no estuviera los arranques seguirían
 * revirtiendo el trabajo del administrador.
 */
@Test
@DisplayName("la marca de personalizada existe con valor por defecto en las plantillas nuevas")
void laMarcaExisteEnLaEntidad() {
NotificationTemplate nueva = new NotificationTemplate();

assertThat(nueva.getCustomTemplate()).isFalse();
}

private com.mesaayuda.entity.NotificationTemplate plantillaConId(UUID id) {
        var t = new com.mesaayuda.entity.NotificationTemplate();
        t.setIdTemplate(id);
        t.setNameTemplate("TICKET_CREADO");
        t.setTypeTemplate("TICKET");
        t.setSubjectTemplate("Asunto");
        t.setBodyTemplate("<p>[imagen]</p>");
        return t;
    }
}