package com.mesaayuda.exception;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Asigna un identificador a cada petición y lo deja disponible para las
 * respuestas de error y para el log.
 * <p>
 * Sirve para poder investigates un fallo que el usuario te reporta sin tener
 * que exposingle el detalle interno de la excepción: te dice "código XYZ" y
 * tu filtras el log por ese código.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String ATRIBUTO = "requestId";
    public static final String HEADER = "X-Request-Id";
    private static final String MDC_KEY = "requestId";

    /**
     * Forma admitida para un identificador de peticion: letras, digitos, punto,
     * guion y guion bajo. Es lo que emiten los gateways habituales y descarta
     * cualquier otra cosa.
     */
    private static final String IDENTIFICADOR = "[A-Za-z0-9._-]{1,64}";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = resolve(request);
        request.setAttribute(ATRIBUTO, requestId);
        response.setHeader(HEADER, requestId);
        MDC.put(MDC_KEY, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /**
     * Si el cliente ya trae un identificador (por ejemplo desde un gateway o un
     * balanceador) se respeta, para no perder la trazabilidad de extremo a
     * extremo. Si no, se genera uno.
     *
     * <p>El valor entrante solo se acepta si tiene forma de identificador. Sin
     * esta comprobacion, cualquiera podia mandar hasta 100 caracteres de texto
     * libre en {@code X-Request-Id} y acabarlos en el log: Tomcat impide
     * inyectar saltos de linea, pero no impide ensuciar la traza ni fijar siempre
     * el mismo identificador para colapsar la correlacion de un incidente.
     */
    private String resolve(HttpServletRequest request) {
        String incoming = request.getHeader(HEADER);
        if (incoming != null && incoming.length() <= 64 && incoming.matches(IDENTIFICADOR)) {
            return incoming;
        }
        return UUID.randomUUID().toString();
    }
}
