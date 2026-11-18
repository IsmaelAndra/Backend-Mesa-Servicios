package com.mesaayuda.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Dispositivo de la petición en curso, para la lista "Dispositivos con sesión
 * abierta". Solo se guarda navegador y sistema ("Chrome en Windows"), nunca el
 * User-Agent completo.
 */
public final class DispositivoCliente {

    private DispositivoCliente() {
    }

    /** Datos del dispositivo; vacíos fuera de una petición HTTP. */
    public record Datos(String dispositivo, String ip) {
    }

    public static Datos actual() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes sra)) {
            return new Datos(null, null);
        }
        HttpServletRequest req = sra.getRequest();
        // getRemoteAddr ya es la IP real: WebConfig reescribe la petición detrás del proxy.
        String ip = req.getRemoteAddr();
        return new Datos(describir(req.getHeader("User-Agent")),
                ip == null ? null : ip.substring(0, Math.min(ip.length(), 64)));
    }

    /** "Chrome en Windows", "Safari en iPhone"... o "Navegador desconocido". */
    public static String describir(String ua) {
        if (ua == null || ua.isBlank()) {
            return "Navegador desconocido";
        }
        String navegador;
        if (ua.contains("Edg/") || ua.contains("EdgA/") || ua.contains("EdgiOS/")) {
            navegador = "Edge";
        } else if (ua.contains("OPR/") || ua.contains("Opera")) {
            navegador = "Opera";
        } else if (ua.contains("SamsungBrowser/")) {
            navegador = "Samsung Internet";
        } else if (ua.contains("Firefox/") || ua.contains("FxiOS/")) {
            navegador = "Firefox";
        } else if (ua.contains("Chrome/") || ua.contains("CriOS/")) {
            navegador = "Chrome";
        } else if (ua.contains("Safari/")) {
            navegador = "Safari";
        } else {
            navegador = "Navegador";
        }
        String sistema;
        if (ua.contains("iPhone")) {
            sistema = "iPhone";
        } else if (ua.contains("iPad")) {
            sistema = "iPad";
        } else if (ua.contains("Android")) {
            sistema = "Android";
        } else if (ua.contains("Windows")) {
            sistema = "Windows";
        } else if (ua.contains("Mac OS X") || ua.contains("Macintosh")) {
            sistema = "Mac";
        } else if (ua.contains("CrOS")) {
            sistema = "Chromebook";
        } else if (ua.contains("Linux")) {
            sistema = "Linux";
        } else {
            sistema = null;
        }
        return sistema == null ? navegador : navegador + " en " + sistema;
    }
}
