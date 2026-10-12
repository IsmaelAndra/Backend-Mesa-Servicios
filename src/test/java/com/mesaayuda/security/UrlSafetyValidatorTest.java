package com.mesaayuda.security;

import com.mesaayuda.exception.BadRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Hay dos puntos donde el servidor descarga una URL que escribe un
 * administrador (el logo del reporte y la URL base de la IA). Sin esto, un
 * ADMIN podía apuntarlas a la red interna y leer la respuesta a través del PDF
 * o de la respuesta de la IA: un SSRF con salida, no solo ciego.
 * <p>
 * Los casos usan direcciones literales para no depender de DNS ni de red.
 */
class UrlSafetyValidatorTest {

    private final UrlSafetyValidator validator = new UrlSafetyValidator();

    // ============================ SE RECHAZA ============================

    @ParameterizedTest
    @ValueSource(strings = {
            "http://127.0.0.1/logo.png",              // bucle local
            "http://localhost/logo.png",               // por nombre
            "http://10.0.0.5/logo.png",                // privada de clase A
            "http://172.16.4.4/logo.png",              // privada de clase B
            "http://192.168.1.10/logo.png",            // privada de clase C
            "http://169.254.169.254/latest/meta-data/", // metadatos de la nube
            "http://0.0.0.0/logo.png",                 // cualquier local
            "http://[::1]/logo.png",                   // bucle local v6
            "http://[::ffff:127.0.0.1]/logo.png",      // IPv4 envuelta en IPv6
            "http://[fc00::1]/logo.png",               // única local v6
            "http://100.64.0.1/logo.png",              // CGNAT
            "http://198.18.0.1/logo.png"               // rango de pruebas
    })
    @DisplayName("rechaza cualquier destino dentro de la red interna")
    void rechazaRedInterna(String url) {
        assertThat(validator.esUrlPublica(url))
                .as("debía rechazar " + url)
                .isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "file:///etc/passwd",
            "gopher://127.0.0.1:11211/_stats",
            "ftp://interno/servidor",
            "jar:file:///tmp/x.jar!/",
            "javascript:alert(1)"
    })
    @DisplayName("rechaza esquemas que no son http(s)")
    void rechazaEsquemasPeligrosos(String url) {
        assertThat(validator.esUrlPublica(url)).isFalse();
    }

    @Test
    @DisplayName("rechaza null, vacío y basura")
    void rechazaEntradasInvalidas() {
        assertThat(validator.esUrlPublica(null)).isFalse();
        assertThat(validator.esUrlPublica("")).isFalse();
        assertThat(validator.esUrlPublica("   ")).isFalse();
        assertThat(validator.esUrlPublica("no es una url")).isFalse();
    }

    @Test
    @DisplayName("un host sin resolver se rechaza")
    void rechazaHostInexistente() {
        assertThat(validator.esUrlPublica("http://este-no-existe-xyz.invalid/logo.png")).isFalse();
    }

    @Test
    @DisplayName("el mensaje de error no devuelve la URL interna que el usuario escribió")
    void elMensajeNoDevuelveLaUrl() {
        assertThatThrownBy(() -> validator.exigirUrlPublica("http://10.1.2.3/secreto", "El logo"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("El logo")
                .hasMessageNotContaining("10.1.2.3")
                .hasMessageNotContaining("secreto");
    }

    // ============================ SE ADMITE ============================

    @Test
    @DisplayName("admite una dirección pública por número")
    void admitePublica() {
        assertThat(validator.esUrlPublica("http://8.8.8.8/logo.png")).isTrue();
    }

    @Test
    @DisplayName("resuelve el nombre en lugar de fiarse solo del texto")
    void resuelveElNombre() {
        // "localhost" pasa el filtro por texto pero resuelve a 127.0.0.1, así
        // que tiene que rechazarlo. No se usa un dominio externo porque el
        // test no puede depender de DNS.
        assertThat(validator.esUrlPublica("http://localhost:8080/logo.png")).isFalse();
    }

    @Test
    @DisplayName("no se rompe con mayúsculas ni espacios alrededor")
    void ignoraEspaciosYMayusculas() {
        assertThat(validator.esUrlPublica("  HTTPS://8.8.8.8/logo.png  ")).isTrue();
    }

    @Test
    @DisplayName("exigirUrlPublica no lanza con una URL válida")
    void exigirNoLanzaConValida() {
        assertThatCode(() -> validator.exigirUrlPublica("https://8.8.8.8/logo.png", "El logo"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("las rutas locales de /uploads no pasan por el validador de red")
    void rutasLocalesNoSeValidanComoRed() {
        // El servidor guarda así los archivos que sube la propia aplicación, y
        // no se descargan por red, así que no aplica la comprobación.
        assertThat(validator.esUrlPublica("/uploads/logo_123.png")).isFalse();
    }
}
