package com.mesaayuda.activos;

import com.mesaayuda.entity.Asset;
import com.mesaayuda.exception.BadRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static com.mesaayuda.IdsPrueba.id;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Numeración configurable, archivos verificados por contenido y PDF del acta y de las etiquetas. */
class ActivosFase4Test {

    @Test
    @DisplayName("el formato de numeración arma TI-LAP-000123 y ACTA-año-0001")
    void numeracion() {
        assertEquals("TI-LAP-000123", NumeracionActivos.aplicar("TI-{TIPO}-{NUM:6}", "lap", 123));
        assertEquals("ACTA-" + LocalDate.now().getYear() + "-0001", NumeracionActivos.aplicar("ACTA-{ANIO}-{NUM:4}", "", 1));
        assertEquals("INV7", NumeracionActivos.aplicar("INV{NUM}", "", 7));
    }

    @Test
    @DisplayName("un formato sin {NUM} o con caracteres raros se rechaza")
    void formatoInvalido() {
        assertThrows(BadRequestException.class, () -> NumeracionActivos.validarFormato("TI-{TIPO}", "código"));
        assertThrows(BadRequestException.class, () -> NumeracionActivos.validarFormato("<script>{NUM}", "código"));
        NumeracionActivos.validarFormato("TI-{TIPO}-{NUM:6}", "código");
    }

    @Test
    @DisplayName("el tipo del archivo se comprueba por sus bytes, no por la extensión")
    void firmaDeArchivos() {
        assertTrue(AssetArchivoService.coincide("pdf", "%PDF-1.7".getBytes()));
        assertFalse(AssetArchivoService.coincide("pdf", "MZ\u0090\u0000".getBytes()));
        assertTrue(AssetArchivoService.coincide("jpg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0}));
        assertFalse(AssetArchivoService.coincide("png", new byte[]{(byte) 0xFF, (byte) 0xD8, 0, 0}));
    }

    @Test
    @DisplayName("el acta sale en PDF con el equipo principal y los demás como accesorios")
    void actaEnPdf() {
        AssetConfig c = new AssetConfig();
        c.setCodigoFormulario("FOR-POL-TIC-01-10");
        c.setVersionFormulario("V01.00");
        ActaActivoService.Persona custodio = new ActaActivoService.Persona(id(1), "Ana Pérez", "Contadora", "ana@coac");
        ActaActivoService.Persona ti = new ActaActivoService.Persona(id(2), "Luis Mora", null, "luis@coac");
        ActaActivoService.ActaVista acta = new ActaActivoService.ActaVista(id(9), "ACTA-2026-0001", "ENTREGA", "ACEPTADA",
                LocalDate.of(2026, 11, 11), "Quito", "Entrega de equipos tecnológicos", null, custodio, null, ti,
                custodio, null, null, "Matriz", null, OffsetDateTime.now(), false, null,
                List.of(new ActaActivoService.ItemVista(id(20), true, "TI-LAP-000001", "Laptop", "Laptop Dell",
                                "Dell Latitude", "SN1", "NUEVO", "Cargador", "Windows 11, Office", null),
                        new ActaActivoService.ItemVista(id(21), false, "TI-MOU-000003", "Mouse", "Mouse",
                                "Logitech M90", null, "BUENO", null, null, "Rayado")),
                false, true, OffsetDateTime.now());
        byte[] pdf = new ActaPdf().generar(acta, c, "Finanzas", "1712345678");
        assertTrue(pdf.length > 1000);
        assertEquals("%PDF", new String(pdf, 0, 4));
    }

    @Test
    @DisplayName("las etiquetas salen en PDF con código de barras")
    void etiquetas() {
        Asset a = new Asset();
        a.setCodeAsset("TI-LAP-000001");
        a.setNameAsset("Laptop Dell Latitude");
        byte[] pdf = new EtiquetasPdf().generar(List.of(a, a, a, a));
        assertEquals("%PDF", new String(pdf, 0, 4));
    }
}
