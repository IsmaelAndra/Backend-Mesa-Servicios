package com.mesaayuda.security;

import com.mesaayuda.exception.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * Comprueba que una URL apunte a internet y no a la red interna.
 * <p>
 * Hay dos sitios donde el servidor descarga una URL que escribe un
 * administrador: el logo de la cabecera de los reportes y la URL base del
 * proveedor de IA. Sin esta comprobación, un ADMIN podía apuntarlas a
 * {@code http://169.254.169.254/...} (los metadatos de la nube, con las
 * credenciales de la instancia) o a un servicio interno, y el resultado
 * acababa dentro del PDF o de la respuesta, es decir, descargable. Eso es un
 * SSRF con salida: no solo se alcanza el servicio interno, se ve la respuesta.
 * <p>
 * Se resuelve el nombre y se rechazan todas las direcciones privadas, de bucle
 * local, enlace local, multicast o reservadas. En IPv6 también se rechazan las
 * direcciones únicas locales (fc00::/7) y las mapeadas a IPv4, porque
 * {@code ::ffff:127.0.0.1} esquivaría una comprobación que solo mirase el
 * prefijo de la cadena.
 * <p>
 * Limitación conocida: entre validar y descargar hay una ventana en la que un
 * nombre malicioso podría cambiar su DNS (DNS rebinding). Cerrarlo del todo
 * obliga a conectar directamente a la IP validada; aquí no se hace, y conviene
 * tenerlo en cuenta si el sistema llega a exponerse fuera de la red interna.
 */
@Component
public class UrlSafetyValidator {

    private static final Logger log = LoggerFactory.getLogger(UrlSafetyValidator.class);

    private static final String[] ESQUEMAS_PERMITIDOS = {"http", "https"};

    /**
     * Lanza BadRequestException si la URL no es segura de descargar. El mensaje
     * no incluye la URL completa para no devolver al cliente un destino interno
     * que el propio usuario acaba de escribir.
     */
    public void exigirUrlPublica(String valor, String nombreCampo) {
        if (!esUrlPublica(valor)) {
            throw new BadRequestException(
                    nombreCampo + " debe ser una dirección web pública (http o https) "
                            + "que no apunte a la red interna.");
        }
    }

    public boolean esUrlPublica(String valor) {
        if (valor == null || valor.isBlank()) {
            return false;
        }
        URI uri;
        try {
            uri = URI.create(valor.trim());
        } catch (IllegalArgumentException e) {
            return false;
        }

        String esquema = uri.getScheme() == null ? null : uri.getScheme().toLowerCase(Locale.ROOT);
        if (esquema == null || !esEsquemaPermitido(esquema)) {
            return false;
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return false;
        }

        InetAddress[] direcciones;
        try {
            direcciones = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            return false;
        }
        if (direcciones.length == 0) {
            return false;
        }
        for (InetAddress direccion : direcciones) {
            if (!esPublica(direccion)) {
                log.warn("Se rechaza una URL que resuelve a una dirección interna ({})", host);
                return false;
            }
        }
        return true;
    }

    private boolean esEsquemaPermitido(String esquema) {
        for (String permitido : ESQUEMAS_PERMITIDOS) {
            if (permitido.equals(esquema)) {
                return true;
            }
        }
        return false;
    }

    /** ¿La dirección es de internet? Cualquier rango reservado se rechaza. */
    public boolean esPublica(InetAddress direccion) {
        if (direccion == null) {
            return false;
        }
        // mapped: una IPv4 envuelta en IPv6 sigue siendo la IPv4 de origen.
        if (direccion.isAnyLocalAddress()
                || direccion.isLoopbackAddress()
                || direccion.isLinkLocalAddress()
                || direccion.isSiteLocalAddress()
                || direccion.isMulticastAddress()) {
            return false;
        }
        if (direccion instanceof Inet6Address v6) {
            if (esUnicaLocalV6(v6) || esReservadaV6(v6)) {
                return false;
            }
        }
        if (direccion instanceof Inet4Address v4 && esReservadaV4(v4)) {
            return false;
        }
        return true;
    }

    /** fc00::/7, las "únicas locales" de IPv6, que isSiteLocalAddress no cubre. */
    private boolean esUnicaLocalV6(Inet6Address direccion) {
        byte[] b = direccion.getAddress();
        if (b == null || b.length < 1) {
            return false;
        }
        int primero = b[0] & 0xFF;
        return (primero & 0xFE) == 0xFC;
    }

    /** 100.64/10 (CGNAT) y 198.18/15 (benchmarks), que no son de internet. */
    private boolean esReservadaV6(Inet6Address direccion) {
        byte[] b = direccion.getAddress();
        if (b == null || b.length < 2) {
            return false;
        }
        int primero = b[0] & 0xFF;
        int segundo = b[1] & 0xFF;
        if (primero == 0xFD && (segundo & 0xC0) == 0xC0) { // fd00::/8, multicast v6
            return true;
        }
        return false;
    }

    private boolean esReservadaV4(Inet4Address direccion) {
        byte[] b = direccion.getAddress();
        if (b == null || b.length != 4) {
            return false;
        }
        int a = b[0] & 0xFF;
        int c = b[1] & 0xFF;
        // 100.64.0.0/10: espacio compartido de los operadores
        if (a == 100 && (c & 0xC0) == 64) {
            return true;
        }
        // 192.0.0.0/24 y 198.18.0.0/15: reservadas para pruebas
        if (a == 192 && b[1] == 0 && (b[2] & 0xFF) == 0) {
            return true;
        }
        if (a == 198 && (c == 18 || c == 19)) {
            return true;
        }
        return false;
    }
}
