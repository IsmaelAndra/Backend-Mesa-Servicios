package com.mesaayuda.mantenimiento;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Una copia .zip manipulada no puede escribir fuera de la carpeta de archivos. */
class ArchivosCopiaTest {

    private final Path destino = Paths.get("/tmp/copia/archivos").toAbsolutePath().normalize();

    @Test
    void aceptaLosArchivosDeLaCopia() {
        assertEquals(destino.resolve("publico/logo.png"),
                ArchivosCopia.destinoSeguro(destino, "archivos/publico/logo.png"));
        assertEquals(destino.resolve("adjuntos/activos/a/foto.jpg"),
                ArchivosCopia.destinoSeguro(destino, "archivos/adjuntos/activos/a/foto.jpg"));
    }

    @Test
    void rechazaRutasQueSalenDeLaCarpeta() {
        assertNull(ArchivosCopia.destinoSeguro(destino, "archivos/../../etc/passwd"));
        assertNull(ArchivosCopia.destinoSeguro(destino, "archivos//etc/passwd"));
        assertNull(ArchivosCopia.destinoSeguro(destino, "archivos/..\\..\\x"));
        assertNull(ArchivosCopia.destinoSeguro(destino, "archivos/"));
    }

    @Test
    void ignoraLoQueNoEsDeLaCopia() {
        assertNull(ArchivosCopia.destinoSeguro(destino, "otra/cosa.txt"));
        assertNull(ArchivosCopia.destinoSeguro(destino, "/archivos/x"));
        assertNull(ArchivosCopia.destinoSeguro(destino, null));
    }
}
