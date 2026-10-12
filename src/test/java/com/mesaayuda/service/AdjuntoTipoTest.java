package com.mesaayuda.service;

import com.mesaayuda.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tipos de archivo admitidos en los adjuntos de ticket.
 *
 * <p>Se fija que la extensión manda, que el contenido tiene que corresponder con
 * ella y que el MIME guardado no es el que declara el navegador.
 */
@ExtendWith(MockitoExtension.class)
class AdjuntoTipoTest {

    private static final byte[] PDF = "%PDF-1.7\n...".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] ZIP = {'P', 'K', 3, 4, 20, 0, 0, 0};
    private static final byte[] EXE = {'M', 'Z', (byte) 0x90, 0, 3, 0};

    @InjectMocks
    private TicketService ticketService;

    private MockMultipartFile archivo(String nombre, String mimeDelCliente, byte[] contenido) {
        return new MockMultipartFile("file", nombre, mimeDelCliente, contenido);
    }

    @Test
    void pdfValidoSeAdmiteConSuMimeReal() {
        // El navegador dice text/html; se guarda lo que corresponde a la extensión.
        assertEquals("application/pdf",
                ticketService.validarTipoAdjunto("informe.pdf", archivo("informe.pdf", "text/html", PDF)));
    }

    @Test
    void xlsxValidoSeAdmite() {
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                ticketService.validarTipoAdjunto("Cuadre.XLSX", archivo("Cuadre.XLSX", null, ZIP)));
    }

    @Test
    void ejecutableSeRechaza() {
        assertThrows(BadRequestException.class,
                () -> ticketService.validarTipoAdjunto("setup.exe", archivo("setup.exe", "application/pdf", EXE)));
    }

    @Test
    void dobleExtensionNoEngaña() {
        assertThrows(BadRequestException.class,
                () -> ticketService.validarTipoAdjunto("factura.pdf.exe", archivo("factura.pdf.exe", null, EXE)));
    }

    @Test
    void htmlYSvgSeRechazan() {
        byte[] html = "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8);
        assertThrows(BadRequestException.class,
                () -> ticketService.validarTipoAdjunto("x.html", archivo("x.html", "text/html", html)));
        assertThrows(BadRequestException.class,
                () -> ticketService.validarTipoAdjunto("x.svg", archivo("x.svg", "image/svg+xml", html)));
    }

    @Test
    void ejecutableRenombradoAPdfSeRechaza() {
        assertThrows(BadRequestException.class,
                () -> ticketService.validarTipoAdjunto("informe.pdf", archivo("informe.pdf", "application/pdf", EXE)));
    }

    @Test
    void ejecutableRenombradoATxtSeRechaza() {
        assertThrows(BadRequestException.class,
                () -> ticketService.validarTipoAdjunto("notas.txt", archivo("notas.txt", "text/plain", EXE)));
    }

    @Test
    void textoPlanoSeAdmite() {
        byte[] csv = "fecha;monto\n2026-10-01;15.30\n".getBytes(StandardCharsets.UTF_8);
        assertEquals("text/csv", ticketService.validarTipoAdjunto("datos.csv", archivo("datos.csv", null, csv)));
    }

    @Test
    void sinExtensionSeRechaza() {
        assertThrows(BadRequestException.class,
                () -> ticketService.validarTipoAdjunto("LEEME", archivo("LEEME", null, PDF)));
    }
}
