package com.mesaayuda.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La propiedad app.cors.allowed-origins (que sale de CORS_ORIGINS) se
 * declaraba en application.yml pero no se leía en ningún sitio: la lista de
 * orígenes estaba escrita a mano en el código. Cambiar la variable de entorno
 * no cambiaba nada.
 */
class SecurityConfigCorsTest {

    private CorsConfiguration configuracion(String origenes) {
        SecurityConfig config = new SecurityConfig();
        UrlBasedCorsConfigurationSource source =
                (UrlBasedCorsConfigurationSource) config.corsConfigurationSource(origenes);
        CorsConfiguration c = source.getCorsConfigurations().get("/**");
        return c;
    }

    @Test
    @DisplayName("usa los orígenes que llegan por configuración")
    void usaLosOrigenesConfigurados() {
        CorsConfiguration c = configuracion("https://soporte.coac.gob.ec,https://ayuda.coac.gob.ec");

        assertThat(c.getAllowedOriginPatterns())
                .containsExactly("https://soporte.coac.gob.ec", "https://ayuda.coac.gob.ec");
    }

    @Test
    @DisplayName("admite el comodín de subdominios que se usa en desarrollo")
    void admiteComodin() {
        CorsConfiguration c = configuracion("http://localhost:*");

        assertThat(c.getAllowedOriginPatterns()).containsExactly("http://localhost:*");
    }

    @Test
    @DisplayName("si la lista viene vacía o con huecos, cae al origen de desarrollo")
    void caeAlDefaultSiVieneVacia() {
        assertThat(configuracion("").getAllowedOriginPatterns())
                .containsExactly("http://localhost:4200");
        assertThat(configuracion("   ,  , ").getAllowedOriginPatterns())
                .containsExactly("http://localhost:4200");
    }

    @Test
    @DisplayName("ignora los espacios sobrantes de la lista")
    void ignoraEspacios() {
        CorsConfiguration c = configuracion("  https://a.gob.ec ,  https://b.gob.ec  ");

        assertThat(c.getAllowedOriginPatterns())
                .containsExactly("https://a.gob.ec", "https://b.gob.ec");
    }

    @Test
    @DisplayName("mantiene el resto de la configuración de CORS que ya funcionaba")
    void mantieneElResto() {
        CorsConfiguration c = configuracion("https://soporte.coac.gob.ec");

        assertThat(c.getAllowedMethods()).contains("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
        assertThat(c.getAllowedHeaders()).isEqualTo(List.of("*"));
        assertThat(c.getAllowCredentials()).isTrue();
    }
}
