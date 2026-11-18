package com.mesaayuda.mantenimiento;

import com.mesaayuda.exception.BadRequestException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Administración → Base de datos (solo SUPER_ADMIN, siempre con el código de la
 * app de autenticación en la cabecera X-Codigo-Mfa o en el cuerpo).
 */
@RestController
@RequestMapping("/admin/base-datos")
@PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
public class BaseDatosController {

    /** Tamaño máximo de una copia subida. */
    static final long MAX_COPIA = 2L * 1024 * 1024 * 1024;

    private final BaseDatosService servicio;

    public BaseDatosController(BaseDatosService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/estado")
    public BaseDatosService.Estado estado() {
        return servicio.estado();
    }

    public record CodigoRequest(String codigoMfa) {
    }

    /** Paso 1: el código del autenticador da un permiso de descarga de un solo uso. */
    @PostMapping("/descargar")
    public Map<String, String> permisoDescarga(@RequestBody CodigoRequest r) {
        return Map.of("permiso", servicio.autorizarDescarga(r == null ? null : r.codigoMfa()));
    }

    /** Sube una copia (cuerpo binario, sin multipart: una base supera el límite de subida normal). */
    @PutMapping(value = "/restaurar", consumes = "application/octet-stream")
    public ResponseEntity<Map<String, String>> restaurar(
            @RequestHeader(name = "X-Codigo-Mfa", required = false) String codigo,
            HttpServletRequest request) throws IOException {
        // El código se comprueba ANTES de recibir el archivo: la subida puede durar minutos.
        com.mesaayuda.entity.User yo = servicio.prepararRestauracion(codigo);
        Path temporal = null;
        try {
            temporal = Files.createTempFile("mesa-subida-", ".dump");
            try (InputStream in = request.getInputStream(); OutputStream out = Files.newOutputStream(temporal)) {
                byte[] buf = new byte[64 * 1024];
                long total = 0;
                int n;
                while ((n = in.read(buf)) > 0) {
                    total += n;
                    if (total > MAX_COPIA) {
                        throw new BadRequestException("La copia supera el máximo de 2 GB");
                    }
                    out.write(buf, 0, n);
                }
            }
        } catch (IOException | RuntimeException e) {
            servicio.cancelarRestauracion();
            if (temporal != null) {
                Files.deleteIfExists(temporal);
            }
            throw e;
        }
        servicio.restaurar(temporal, yo);
        return ResponseEntity.ok(Map.of("mensaje", "Base restaurada. Vuelva a iniciar sesión."));
    }

    public record RestablecerRequest(String codigoMfa, String confirmacion) {
    }

    @PostMapping("/restablecer")
    public ResponseEntity<Map<String, String>> restablecer(@RequestBody RestablecerRequest r) {
        if (r == null || !"RESTABLECER".equals(r.confirmacion())) {
            throw new BadRequestException("Escriba RESTABLECER para confirmar");
        }
        servicio.restablecer(r.codigoMfa());
        return ResponseEntity.ok(Map.of("mensaje", "Base restablecida. Vuelva a iniciar sesión."));
    }
}
