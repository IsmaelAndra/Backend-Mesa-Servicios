package com.mesaayuda.controller;

import com.mesaayuda.dto.ErrorPageConfigRequest;
import com.mesaayuda.dto.ErrorPageConfigResponse;
import com.mesaayuda.dto.PrivacyConfigRequest;
import com.mesaayuda.dto.PrivacyConfigResponse;
import com.mesaayuda.service.PageContentService;
import com.mesaayuda.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Textos de las pantallas de error y politica de privacidad.
 *
 * Las lecturas de "/public" no llevan ninguna anotacion de seguridad a proposito:
 * la pagina de un 403 o de un 500 se muestra precisamente cuando no hay sesion
 * valida, o cuando el servidor tiene problemas. Si exigiera token, un 403 por
 * rol insuficiente se quedaria en blanco. Lo que si se protege es la escritura,
 * que es solo para super administrador: es contenido institucional, no una
 * preferencia.
 *
 * El permiso va con "@PreAuthorize" a nivel de metodo, y no de clase como en
 * otros controladores, porque el bloque publico de la clase no lleva ninguno.
 */
@RestController
@RequestMapping
@Tag(name = "Páginas públicas", description = "Textos de error y política de privacidad")
public class PageContentController {

    @Autowired
    private PageContentService pageContentService;

    @Autowired
    private UserService userService;

    // ============================ LECTURA PUBLICA ============================

    @GetMapping("/pages/public/error-texts")
    @Operation(summary = "Textos de las pantallas de error (público)")
    public ResponseEntity<ErrorPageConfigResponse> textosDeError() {
        return ResponseEntity.ok(pageContentService.textosDeError());
    }

    @GetMapping("/pages/public/privacy")
    @Operation(summary = "Política de privacidad (público)")
    public ResponseEntity<PrivacyConfigResponse> privacidad() {
        return ResponseEntity.ok(pageContentService.politicaDePrivacidad());
    }

    // ============================ EDICION (SUPER ADMIN) ============================

    @GetMapping("/admin/error-pages")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Leer los textos de error para editarlos")
    public ResponseEntity<ErrorPageConfigResponse> leerTextosDeError() {
        return ResponseEntity.ok(pageContentService.textosDeError());
    }

    @PutMapping("/admin/error-pages")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Guardar los textos de las pantallas de error")
    public ResponseEntity<?> guardarTextosDeError(@Valid @RequestBody ErrorPageConfigRequest request) {
        ErrorPageConfigResponse guardado = pageContentService.guardarTextosDeError(request, idActual());
        return ResponseEntity.ok(guardado);
    }

    @GetMapping("/admin/privacy")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Leer la política de privacidad para editarla")
    public ResponseEntity<PrivacyConfigResponse> leerPrivacidad() {
        return ResponseEntity.ok(pageContentService.politicaDePrivacidad());
    }

    @PutMapping("/admin/privacy")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Guardar la política de privacidad")
    public ResponseEntity<?> guardarPrivacidad(@Valid @RequestBody PrivacyConfigRequest request) {
        PrivacyConfigResponse guardada = pageContentService.guardarPoliticaDePrivacidad(request, idActual());
        return ResponseEntity.ok(guardada);
    }

    /**
     * Id del super administrador que guarda, para la columna updated_by. Se
     * captura sin propagar el error: si falla, se guarda sin autor y el cambio
     * sigue siendo valido, porque el permiso ya lo comprueba PreAuthorize.
     */
    private UUID idActual() {
        try {
            return userService.getCurrentUser().getIdUser();
        } catch (Exception e) {
            return null;
        }
    }
}