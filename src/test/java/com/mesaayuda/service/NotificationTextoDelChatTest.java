package com.mesaayuda.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * El chat guarda el HTML del editor. En el correo llegaba tal cual, escapado:
 * {@code "<p>Prueba</p><p><br></p>"} con las etiquetas a la vista.
 */
class NotificationTextoDelChatTest {

    @Test
    @DisplayName("el HTML del editor queda como texto con sus saltos de linea")
    void quitaLasEtiquetasYConservaLosSaltos() {
        assertEquals("Prueba", NotificationService.textoDelChat("<p>Prueba</p><p><br></p>"));
        assertEquals("Hola\nQue tal", NotificationService.textoDelChat("<p>Hola</p><p>Que tal</p>"));
        assertEquals("linea 1\nlinea 2", NotificationService.textoDelChat("linea 1<br/>linea 2"));
    }

    @Test
    @DisplayName("decodifica las entidades y no deja etiquetas")
    void decodificaEntidades() {
        String texto = NotificationService.textoDelChat("<p>a &lt; b &amp;&nbsp;c <b>negrita</b></p>");
        assertEquals("a < b & c negrita", texto);
        assertFalse(texto.contains("<b>"));
    }

    @Test
    @DisplayName("un mensaje vacio no rompe nada")
    void vacio() {
        assertEquals("", NotificationService.textoDelChat(null));
        assertEquals("", NotificationService.textoDelChat("<p><br></p>"));
    }
}
