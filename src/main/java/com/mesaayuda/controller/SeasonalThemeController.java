package com.mesaayuda.controller;

import com.mesaayuda.dto.SeasonalThemeDtos.ConfigRequest;
import com.mesaayuda.dto.SeasonalThemeDtos.Panel;
import com.mesaayuda.dto.SeasonalThemeDtos.Tema;
import com.mesaayuda.dto.SeasonalThemeDtos.TemaActual;
import com.mesaayuda.dto.SeasonalThemeDtos.TemaRequest;
import com.mesaayuda.entity.SeasonalThemeConfig;
import com.mesaayuda.service.SeasonalThemeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * Temas de temporada. El tema de hoy es público (lo pide el login, antes de la
 * sesión, por eso va bajo /pages/public); la gestión es solo del SUPER_ADMIN.
 */
@RestController
@Tag(name = "Temas de temporada", description = "Adornos de Halloween, Navidad y otras fechas")
public class SeasonalThemeController {

    private final SeasonalThemeService service;

    /** Igual que en AdminController: en producción la API y la app comparten origen. */
    @Value("${app.urls-relativas:false}")
    private boolean urlsRelativas;

    public SeasonalThemeController(SeasonalThemeService service) {
        this.service = service;
    }

    @GetMapping("/pages/public/tema-temporada")
    @Operation(summary = "Tema de temporada de hoy (204 si no hay)")
    public ResponseEntity<TemaActual> actual(HttpServletRequest request) {
        return service.temaActual()
                .map(t -> ResponseEntity.ok(service.aActual(t, url(request))))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/admin/temas-temporada")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Configuración y lista de temas")
    public Panel panel(HttpServletRequest request) {
        return panelDe(request);
    }

    @PutMapping("/admin/temas-temporada/config")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Interruptor general y tema forzado")
    public Panel configurar(@Valid @RequestBody ConfigRequest req, HttpServletRequest request) {
        service.configurar(req);
        return panelDe(request);
    }

    @PutMapping("/admin/temas-temporada/{id}")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Editar nombre, fechas, saludo y estado de un tema")
    public Tema actualizar(@PathVariable UUID id, @Valid @RequestBody TemaRequest req, HttpServletRequest request) {
        return service.aTema(service.actualizar(id, req), url(request));
    }

    @PostMapping(value = "/admin/temas-temporada/{id}/imagen/{tipo}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Subir una imagen del tema: fondo, logo, inicio (dashboard) o icono (pestaña y app)")
    public Tema subirImagen(@PathVariable UUID id, @PathVariable String tipo,
                            @RequestParam("file") MultipartFile file, HttpServletRequest request) {
        return service.aTema(service.subirImagen(id, tipo, file), url(request));
    }

    @DeleteMapping("/admin/temas-temporada/{id}/imagen/{tipo}")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Quitar el fondo o el logo propio (se usan los de Marca)")
    public Tema quitarImagen(@PathVariable UUID id, @PathVariable String tipo, HttpServletRequest request) {
        return service.aTema(service.quitarImagen(id, tipo), url(request));
    }

    private Panel panelDe(HttpServletRequest request) {
        UnaryOperator<String> url = url(request);
        SeasonalThemeConfig c = service.config();
        return new Panel(Boolean.TRUE.equals(c.getEnabledThemeConfig()), c.getForcedCodeTheme(),
                service.temaActual().map(t -> service.aActual(t, url)).orElse(null),
                service.listar().stream().map(t -> service.aTema(t, url)).toList());
    }

    private UnaryOperator<String> url(HttpServletRequest request) {
        return path -> {
            if (path == null || path.isBlank()) return null;
            if (path.startsWith("http")) return path;
            if (urlsRelativas) return request.getContextPath() + path;
            return request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort()
                    + request.getContextPath() + path;
        };
    }
}
