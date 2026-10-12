package com.mesaayuda.controller;

import java.util.UUID;
import static com.mesaayuda.IdsPrueba.id;
import com.mesaayuda.entity.Answer;
import com.mesaayuda.entity.Attach;
import com.mesaayuda.exception.ResourceNotFoundException;
import com.mesaayuda.repository.AttachRepository;
import com.mesaayuda.service.TicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Descarga de adjuntos.
 *
 * Lo que se comprueba aqui es el cierre del agujero que motivo el cambio: los
 * adjuntos estaban bajo /uploads/**, que estaba en permitAll, asi que la URL
 * del archivo bastaba para descargarlo sin sesion y sin mirar permisos. Ahora la
 * descarga pasa por el endpoint y se autoriza con el mismo criterio que el
 * resto del ticket.
 */
class AttachmentControllerTest {

    private TicketService ticketService;
    private AttachRepository attachRepository;
    private AttachmentController controller;

    @BeforeEach
    void setUp() {
        ticketService = mock(TicketService.class);
        attachRepository = mock(AttachRepository.class);
        controller = new AttachmentController(ticketService, attachRepository);
    }

    private Attach adjunto(UUID id, String nombre, String mime) {
        Attach a = new Attach();
        a.setIdAttach(id);
        a.setFileNameAttach(nombre);
        a.setStoragePathAttach("guion de prueba.txt");
        a.setMimeTypeAttach(mime);
        a.setDeletedAttach(false);
        return a;
    }

    @Test
    void descargaElArchivoCuandoElTicketEsLegible() {
        Attach a = adjunto(id(7), "reporte.pdf", "application/pdf");
        when(ticketService.getAttachments(id(7))).thenReturn(List.of(a));
        when(attachRepository.findById(id(7))).thenReturn(Optional.of(a));
        when(ticketService.abrirAdjunto(any(Attach.class)))
                .thenReturn(new ByteArrayResource("contenido".getBytes()));

        ResponseEntity<Resource> r = controller.download(id(7), id(7));

        assertEquals(200, r.getStatusCode().value());
        assertEquals(MediaType.APPLICATION_PDF, r.getHeaders().getContentType());
        // El archivo lo genera la aplicacion: una cache publica serviria el
        // contenido de un adjunto que ya se borro.
        assertEquals("private, no-store", r.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL));
        String cd = r.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertTrue(cd.startsWith("attachment"), cd);
        assertTrue(cd.contains("reporte.pdf"), cd);
    }

    @Test
    void responde404SiElAdjuntoNoEstaEntreLosPermitidos() {
        // getAttachments devuelve la lista ya filtrada por permiso. Si el adjunto
        // pedido no aparece, quien pregunta no puede leerlo.
        Attach otro = adjunto(id(9), "captura.png", "image/png");
        when(ticketService.getAttachments(id(7))).thenReturn(List.of(otro));

        assertThrows(ResourceNotFoundException.class, () -> controller.download(id(7), id(7)));

        // No debe ni llegar a leer la fila: si se leyera, la comprobacion
        // dependeria de que el listado viniera bien filtrado.
        verify(attachRepository, never()).findById(any());
        verify(ticketService, never()).abrirAdjunto(any());
    }

    @Test
    void laAutorizacionSeDecidePorElTicketDeLaRuta() {
        // El adjunto 9 esta permitido en el ticket 7 pero la ruta dice ticket 8,
        // donde no lo esta. Manda el ticket de la URL, no el archivo.
        Attach a = adjunto(id(9), "privado.pdf", "application/pdf");
        when(ticketService.getAttachments(id(8))).thenReturn(List.of());
        when(attachRepository.findById(id(9))).thenReturn(Optional.of(a));

        assertThrows(ResourceNotFoundException.class, () -> controller.download(id(8), id(9)));
        verify(ticketService, never()).abrirAdjunto(any());
    }

    @Test
    void unaNotaInternaNoSeDescargaParaQuienNoEsPersonal() {
        // El listado que getAttachments devuelve a un titular ya viene sin las
        // notas internas. El endpoint no debe reponerlas.
        Answer interna = new Answer();
        interna.setInternalAnswer(true);
        Attach a = adjunto(id(11), "nota-interna.txt", "text/plain");
        a.setAnswerAttach(interna);

        when(ticketService.getAttachments(id(7))).thenReturn(List.of());
        when(attachRepository.findById(id(11))).thenReturn(Optional.of(a));

        assertThrows(ResourceNotFoundException.class, () -> controller.download(id(7), id(11)));
        verify(ticketService, never()).abrirAdjunto(any());
    }

    @Test
    void responde404SiElArchivoYaNoEstaEnDisco() {
        Attach a = adjunto(id(12), "borrado.pdf", "application/pdf");
        when(ticketService.getAttachments(id(7))).thenReturn(List.of(a));
        when(attachRepository.findById(id(12))).thenReturn(Optional.of(a));
        when(ticketService.abrirAdjunto(any(Attach.class))).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () -> controller.download(id(7), id(12)));
    }

    @Test
    void responde404SiElAdjuntoEstaMarcadoComoBorrado() {
        Attach a = adjunto(id(13), "eliminado.pdf", "application/pdf");
        a.setDeletedAttach(true);
        when(ticketService.getAttachments(id(7))).thenReturn(List.of());
        when(attachRepository.findById(id(13))).thenReturn(Optional.of(a));

        assertThrows(ResourceNotFoundException.class, () -> controller.download(id(7), id(13)));
    }

    @Test
    void unMimeInvalidoNoTumbaLaDescarga() {
        // El MIME lo manda el cliente al subir el archivo, asi que puede venir roto.
        // "application/pdf/con imagenes" no es un tipo valido: sobra la segunda barra.
        Attach a = adjunto(id(14), "raro.dat", "application/pdf/con imagenes");
        when(ticketService.getAttachments(id(7))).thenReturn(List.of(a));
        when(attachRepository.findById(id(14))).thenReturn(Optional.of(a));
        when(ticketService.abrirAdjunto(any(Attach.class)))
                .thenReturn(new ByteArrayResource("x".getBytes()));

        ResponseEntity<Resource> r = controller.download(id(7), id(14));

        assertEquals(200, r.getStatusCode().value());
        assertEquals(MediaType.APPLICATION_OCTET_STREAM, r.getHeaders().getContentType());
    }

    @Test
    void elNombreDelArchivoViaCodificadoYConReservaAscii() {
        // El nombre lo elige quien sube: puede traer acentos o comillas, y eso
        // rompe la cabecera Content-Disposition si se escribe tal cual.
        Attach a = adjunto(id(15), "año 2026 \"final\".txt", "text/plain");
        when(ticketService.getAttachments(id(7))).thenReturn(List.of(a));
        when(attachRepository.findById(id(15))).thenReturn(Optional.of(a));
        when(ticketService.abrirAdjunto(any(Attach.class)))
                .thenReturn(new ByteArrayResource("x".getBytes()));

        ResponseEntity<Resource> r = controller.download(id(7), id(15));
        String cd = r.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);

        assertTrue(cd.contains("filename*=UTF-8''"), cd);

        int inicio = cd.indexOf("filename=\"") + "filename=\"".length();
        String ascii = cd.substring(inicio, cd.indexOf('"', inicio));
        assertFalse(ascii.contains("\""), "el nombre ASCII no puede contener comillas: " + cd);
        assertTrue(ascii.chars().allMatch(ch -> ch >= 0x20 && ch <= 0x7E),
                "el nombre ASCII no puede llevar caracteres no ASCII: " + cd);
    }

    @Test
    void sinNombreNiMimeUsaLosValoresPorDefecto() {
        Attach a = adjunto(id(16), null, null);
        when(ticketService.getAttachments(id(7))).thenReturn(List.of(a));
        when(attachRepository.findById(id(16))).thenReturn(Optional.of(a));
        when(ticketService.abrirAdjunto(any(Attach.class)))
                .thenReturn(new ByteArrayResource("x".getBytes()));

        ResponseEntity<Resource> r = controller.download(id(7), id(16));

        assertTrue(r.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).contains("adjunto"));
        assertEquals(MediaType.APPLICATION_OCTET_STREAM, r.getHeaders().getContentType());
    }
}