package com.mesaayuda.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Avisa al arrancar si la aplicación no corre con el perfil de producción ni con
 * el de desarrollo.
 *
 * <p>Las protecciones de producción (cabeceras de seguridad, HSTS, cookie
 * Secure, errores sin detalle, actuator reducido) viven en application-prod.yml.
 * Arrancar en un servidor sin {@code SPRING_PROFILES_ACTIVE=prod} las deja todas
 * apagadas y la aplicación funciona igual, así que nadie se entera. Esta línea
 * en el log es lo que hace visible ese olvido.
 */
@Component
public class PerfilArranqueAviso {

    private static final Logger log = LoggerFactory.getLogger(PerfilArranqueAviso.class);

    private final Environment environment;

    public PerfilArranqueAviso(Environment environment) {
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void avisar() {
        String[] perfiles = environment.getActiveProfiles();
        boolean conocido = Arrays.stream(perfiles).anyMatch(p -> p.equals("prod") || p.equals("dev"));
        if (!conocido) {
            log.warn("La aplicación arrancó sin el perfil 'prod' ni 'dev' (perfiles activos: {}). "
                    + "Las cabeceras de seguridad, la cookie Secure y los errores sin detalle están "
                    + "desactivados. En un servidor, arranca con SPRING_PROFILES_ACTIVE=prod.",
                    perfiles.length == 0 ? "ninguno" : String.join(",", perfiles));
        }
    }
}
