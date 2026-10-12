package com.mesaayuda.service;

import com.mesaayuda.entity.Ticket;
import com.mesaayuda.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.security.access.AccessDeniedException;

import java.util.UUID;

/**
 * Quién puede hacer qué con un ticket.
 *
 * Antes estas reglas vivían como métodos privados de TicketService, así que solo
 * se aplicaban a las operaciones que pasaban por ese servicio. RatingService y
 * AuditService tenían su propia consulta del ticket y su propia idea del
 * permiso, y en los dos casos no había ninguna: con solo tener sesión iniciada
 * bastaba para pedir /tickets/{id}/rating o /tickets/{id}/audit de un ticket
 * ajeno escribiendo su id en la URL. Un IDOR no es un fallo de permisos de
 * usuario, es un fallo de autorización por recurso.
 *
 * Al sacarlas a una clase aparte hay una sola definición de "puede leer esto" y
 * todos los caminos pasan por ella. Añadir un caso nuevo (por ejemplo, que un
 * supervisor de equipo pueda ver los tickets de su equipo) es cambiar un sitio.
 */
@Component
public class TicketAccessPolicy {

    private static final Logger log = LoggerFactory.getLogger(TicketAccessPolicy.class);

    @Autowired
    private UserService userService;

    /** El usuario con sesión ahora mismo. */
    public User actual() {
        return userService.getCurrentUser();
    }

    /**
     * Personal de la mesa de servicios: agente, administrador o super
     * administrador.
     *
     * <p>Si el usuario o su rol vienen a null se devuelve false, no una
     * excepción: en una comprobación de seguridad, fallar hacia el permiso
     * abierto sería un agujero, y reventar aquí se traduciría en un 500 en vez
     * de un 403.
     */
    public boolean esPersonal(User u) {
        if (u == null || u.getRolUser() == null) {
            return false;
        }
        String rol = u.getRolUser().getNameRole();
        return "AGENT".equals(rol) || "ADMIN".equals(rol) || "SUPER_ADMIN".equals(rol);
    }

    /** Solo administradores; un agente no entra aunque tenga el ticket asignado. */
    public boolean esPersonalAdministrador(User u) {
        if (u == null || u.getRolUser() == null) {
            return false;
        }
        String rol = u.getRolUser().getNameRole();
        return "ADMIN".equals(rol) || "SUPER_ADMIN".equals(rol);
    }

    /** El titular del ticket. */
    public boolean esPropietario(Ticket t, User u) {
        return t != null && u != null
                && t.getUserTicket() != null
                && t.getUserTicket().getIdUser().equals(u.getIdUser());
    }

    /** El agente que tiene el ticket asignado. */
    public boolean esAgenteAsignado(Ticket t, User u) {
        return t != null && u != null
                && t.getAgentTicket() != null
                && t.getAgentTicket().getIdUser().equals(u.getIdUser());
    }

    /**
     * Si una cuenta está habilitada.
     *
     * <p>No se puede comprobar con {@code Boolean.TRUE.equals(statusUser)} porque
     * {@code statusUser} es un String: eso da siempre {@code false} para
     * "ACTIVE", de modo que la condición de descarte se cumpliría siempre.
     */
    public boolean esActivo(User u) {
        return u != null
                && u.getStatusUser() != null
                && "ACTIVE".equalsIgnoreCase(u.getStatusUser().trim());
    }

    /** Puede LEER: el titular o cualquier miembro del personal. */
    public boolean puedeLeer(Ticket t, User u) {
        return esPropietario(t, u) || esPersonal(u);
    }

    /** Puede ESCRIBIR: el titular, el agente asignado o un administrador. */
    public boolean puedeEscribir(Ticket t, User u) {
        return esPropietario(t, u) || esAgenteAsignado(t, u) || esPersonalAdministrador(u);
    }

    /**
     * Corta el acceso si el usuario actual no puede leer el ticket.
     *
     * <p>Se registra el intento antes de lanzar. Un 403 sin rastro en el log es
     * un ataque por fuerza bruta sobre ids que no devuelve nada.
     */
    public void exigirLectura(Ticket t, String queSeHacia, UUID id) {
        User actual = actual();
        if (!puedeLeer(t, actual)) {
            log.warn("Acceso denegado: {} intentó {} del ticket #{}.",
                    actual != null ? actual.getCorporateEmailUser() : "desconocido", queSeHacia, id);
            throw new AccessDeniedException("No tiene acceso a este ticket");
        }
    }
}