package com.mesaayuda.config;

/**
 * Nombres de las dos zonas dentro de la carpeta de uploads.
 *
 * La separacion es la barrera de seguridad de los adjuntos: lo que no esta en
 * {@link #PUBLICO} no lo sirve el handler de recursos estaticos y por tanto no
 * se puede descargar sin pasar por el endpoint autenticado.
 *
 *   PUBLICO    recursos de marca, visibles en el login sin sesion.
 *   ADJUNTOS  archivos subidos a un ticket, siempre requieren permiso de lectura.
 */
public final class UploadZones {

    /** Logo, icono del sistema, fondos del chat, logos de informe y avatares. */
    public static final String PUBLICO = "publico";

    /** Archivos de ticket. Se sirven por AttachmentController, no como estatico. */
    public static final String ADJUNTOS = "adjuntos";

    private UploadZones() {
    }
}