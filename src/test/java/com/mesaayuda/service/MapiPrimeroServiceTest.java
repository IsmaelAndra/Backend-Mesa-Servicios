package com.mesaayuda.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** La respuesta de MAPI (Markdown de la IA) pasa a HTML seguro para el chat. */
class MapiPrimeroServiceTest {

    @Test
    void parrafosListasYNegritas() {
        String html = MapiPrimeroService.aHtml("Hola, le ayudo.\n\n1. Reinicie el **equipo**.\n2. Pruebe de nuevo.\n\n- Opción A\n- Opción B\nGracias");
        assertEquals("<p>Hola, le ayudo.</p><ol><li>Reinicie el <b>equipo</b>.</li><li>Pruebe de nuevo.</li></ol>"
                + "<ul><li>Opción A</li><li>Opción B</li></ul><p>Gracias</p>", html);
    }

    @Test
    void escapaElHtmlQueTraigaLaIa() {
        String html = MapiPrimeroService.aHtml("Hola <script>alert(1)</script> <img src=x onerror=y>");
        assertFalse(html.contains("<script"));
        assertFalse(html.contains("<img"));
        assertTrue(html.contains("&lt;script&gt;"));
    }
}
