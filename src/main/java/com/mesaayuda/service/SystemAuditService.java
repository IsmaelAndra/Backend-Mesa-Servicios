package com.mesaayuda.service;
import java.util.UUID;

import com.mesaayuda.entity.AuditLog;
import com.mesaayuda.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Auditoria de acciones administrativas y de autenticacion.
 *
 * <p>Guarda quien hizo que, sobre que entidad, cuando y desde donde. Es el
 * unico rastro estructurado del que dispone el sistema para reconstruir un
 * incidente, asi que se amplia en lugar de crear un segundo mecanismo: las tres
 * tablas que ya existen (audit_log, ticket_audit, graph_config_audit) siguen
 * tendo lo suyo.
 *
 * <p><b>Transacciones.</b> La propagacion por defecto es REQUIRES_NEW y no
 * REQUIRED, y no es un detalle menor. La mayoria de las acciones que se auditan
 * terminan en una excepcion: un login fallido, un alta duplicada, una
 * operacion denegada. Con REQUIRED, el registro se unia a la transaccion de esa
 * llamada y la excepcion la marcaba para rollback, con lo que el INSERT
 * desaparecia. Ese fue el caso de LOGIN_FAILED: el bloqueo por fuerza bruta
 * funcionaba porque LoginAttemptService si usa REQUIRES_NEW, pero la tabla que
 * deberia conservar la evidencia del ataque era justo la que lo perdia, y
 * audit_log no contenia ni un solo login fallido.
 *
 * <p><b>Direccion IP.</b> Se toma de getRemoteAddr, que detras de un proxy
 * devuelve la del balanceador. Se resuelve con el filtro de cabeceras
 * adelantadas, que Spring activa con server.forward-headers-strategy, de modo
 * que la IP que llega es la del cliente real y no la que elige quien llama.
 */
@Service
public class SystemAuditService {

    private static final Logger log = LoggerFactory.getLogger(SystemAuditService.class);

    @Autowired
    private AuditLogRepository auditLogRepository;

    /** Registro simple: el actor se deduce de la sesion. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String entity, UUID entityId) {
        record(action, entity, entityId, currentUsername(), null);
    }

    /** Registro indicando el actor de forma explicita, para acciones de otra cuenta. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String entity, UUID entityId, String username) {
        record(action, entity, entityId, username, null);
    }

    /**
     * Registro con detalle de que cambio.
     *
     * <p>Es lo que hace util la auditoria. Sin el, un cambio de rol de AGENT a
     * ADMIN queda registrado como un USER_UPDATE indistinguible de un cambio de
     * telefono, y no hay forma de saber que paso ni de reconstruir un ataque.
     *
     * @param details que cambio exactamente, por ejemplo "ACTIVE -> INACTIVE"
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String entity, UUID entityId, String username, String details) {
        AuditLog registro = new AuditLog();
        registro.setAction(action);
        registro.setEntity(entity);
        registro.setEntityId(entityId);
        registro.setUsername(username);
        registro.setDetails(details);
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
// La IP la resuelve ClientIpFilter y deja en el atributo. Con un proxy delante
                  // es la real, sin proxy es la conexion directa: en ningun caso es
                  // un valor que pueda elegir quien envía la peticion.
                  registro.setHttpMethod(request.getMethod());
                registro.setRequestUri(recortar(request.getRequestURI(), 300));
                // El mismo identificador que RequestIdFilter pone en el atributo
                // de la peticion y en el MDC, para poder unir una fila de la tabla
                // con su linea de log.
Object ip = request.getAttribute(
                          com.mesaayuda.security.ClientIpFilter.ATRIBUTO_IP);
                  registro.setIpAddress(ip != null ? String.valueOf(ip) : request.getRemoteAddr());
                  Object id = request.getAttribute(
                          com.mesaayuda.exception.RequestIdFilter.ATRIBUTO);
                  if (id != null) {
                      registro.setRequestId(recortar(String.valueOf(id), 64));
                }
            }
        } catch (Exception ignored) {
            // Sin contexto de peticion (schedulers, arranque). Se registra igual.
        }
        auditLogRepository.save(registro);
    }

    /** Un valor de configuracion sensible no debe acabar en el log de auditoria. */
    private static String recortar(String valor, int max) {
        if (valor == null) {
            return null;
        }
        return valor.length() <= max ? valor : valor.substring(0, max);
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof UserDetails details)) {
            return null;
        }
        return details.getUsername();
    }
}