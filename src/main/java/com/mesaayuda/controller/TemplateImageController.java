package com.mesaayuda.controller;

import com.mesaayuda.entity.NotificationTemplateImage;
import com.mesaayuda.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/**
 * Sirve las imagenes de las plantillas de notificacion, que estan guardadas en
 * la base y no en disco.
 *
 * El endpoint es publico a proposito: el cliente de correo del destinatario
 * abre el HTML del aviso desde su propio servidor de correo, sin sesion ni
 * token de la aplicacion. Por eso la ruta lleva un token aleatorio en vez del
 * id de la fila, que seria correlativo y se podria recorrer.
 */
@RestController
@RequestMapping("/templates")
public class TemplateImageController {

    private final AdminService adminService;

    public TemplateImageController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/image/{token}")
    @Operation(summary = "Imagen de una plantilla de notificación, almacenada en la base")
    public ResponseEntity<byte[]> imagen(@PathVariable String token) {
        NotificationTemplateImage image = adminService.getTemplateImage(token);
        if (image == null || image.getDataImage() == null || image.getDataImage().length == 0) {
            // Sin cuerpo ni redireccion: un token viejo o equivocado devuelve 404.
            return ResponseEntity.notFound().build();
        }
        MediaType tipo;
        try {
            tipo = MediaType.parseMediaType(image.getMimeTypeImage());
        } catch (Exception e) {
            tipo = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .contentType(tipo)
                .contentLength(image.getDataImage().length)
                // El tipo se fijó al subir la imagen comprobando la firma, asi que
                // el navegador no debe volver a interpretarlo: sin esto, un
                // contenido con HTML disfrazado de imagen podria ejecutarse.
                .header("X-Content-Type-Options", "nosniff")
                // El token no cambia nunca para una imagen dada, asi que se cachea
                // un rato: el mismo correo puede pedirla varias veces.
                .header(HttpHeaders.CACHE_CONTROL,
                        "public, max-age=" + TimeUnit.DAYS.toSeconds(30))
                .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic())
                .body(image.getDataImage());
    }
}