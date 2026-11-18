package com.mesaayuda.mantenimiento;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * Paso 2 de la descarga de la base: un enlace normal (sin cabecera Authorization)
 * que vale una sola vez durante 60 s. El permiso lo da POST /admin/base-datos/descargar
 * al SUPER_ADMIN con su código del autenticador. Ruta abierta en SecurityConfig.
 *
 * <p>Se escribe directamente en la respuesta (sin StreamingResponseBody: su tiempo
 * máximo asíncrono cortaría una base grande).
 */
@RestController
public class BaseDatosDescargaController {

    private final BaseDatosService servicio;

    public BaseDatosDescargaController(BaseDatosService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/base-datos/descarga/{permiso}")
    public void descargar(@PathVariable String permiso, HttpServletResponse response) throws IOException {
        servicio.consumirPermiso(permiso);
        response.setContentType("application/octet-stream");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + servicio.nombreCopia() + "\"");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Accel-Buffering", "no");
        servicio.descargar(response.getOutputStream());
    }
}
