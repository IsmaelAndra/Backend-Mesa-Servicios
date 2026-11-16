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

    /** Puede faltar en pruebas unitarias que construyen la política a mano. */
    @Autowired(required = false)
    private JefaturaService jefaturaService;

    @Autowired(required = false)
    private com.mesaayuda.repository.TicketAutorizadoRepository ticketAutorizadoRepository;

    @Autowired(required = false)
    private com.mesaayuda.repository.AccesoConfidencialRepository accesoConfidencialRepository;

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
        return com.mesaayuda.security.Roles.esPersonal(rol);
    }

    /** Solo el súper admin; un agente no entra aunque tenga el ticket asignado. */
    public boolean esPersonalAdministrador(User u) {
        if (u == null || u.getRolUser() == null) {
            return false;
        }
        String rol = u.getRolUser().getNameRole();
        return "SUPER_ADMIN".equals(rol);
    }

    /** Jefe del área que atiende el ticket. */
    public boolean esJefeDelArea(Ticket t, User u) {
        return jefaturaService != null && t != null && t.getAreaDestinoTicket() != null
                && jefaturaService.esJefeDe(u, t.getAreaDestinoTicket().getIdArea());
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
    /** Qué tickets puede ver la persona; la misma regla para listas, detalle y eventos. */
    public AlcanceTickets alcanceDe(User u) {
        if (u == null || u.getRolUser() == null) {
            return AlcanceTickets.soloPropios(u == null ? null : u.getIdUser());
        }
        String rol = u.getRolUser().getNameRole();
        java.util.Set<UUID> permitidos = confidencialesPermitidos(u);
        if (com.mesaayuda.security.Roles.veTodo(rol)) {
            return new AlcanceTickets(true, u.getIdUser(), java.util.Set.of(), java.util.Set.of(),
                    java.util.Set.of(), permitidos);
        }
        if (com.mesaayuda.security.Roles.esJefe(rol)) {
            // Lo que atiende su área y, en seguimiento, lo que pide su gente.
            java.util.Set<UUID> areas = areasDelJefe(u);
            return new AlcanceTickets(false, u.getIdUser(), areas, areas, areas, permitidos);
        }
        if (com.mesaayuda.security.Roles.AGENTE.equals(rol)) {
            java.util.Set<UUID> suya = u.getArea() == null ? java.util.Set.of() : java.util.Set.of(u.getArea().getIdArea());
            return new AlcanceTickets(false, u.getIdUser(), suya, java.util.Set.of(), java.util.Set.of(), permitidos);
        }
        return new AlcanceTickets(false, u.getIdUser(), java.util.Set.of(), java.util.Set.of(), java.util.Set.of(), permitidos);
    }

    /** Confidenciales a los que se autorizó a la persona o tiene acceso de emergencia vigente. */
    private java.util.Set<UUID> confidencialesPermitidos(User u) {
        java.util.Set<UUID> ids = new java.util.HashSet<>();
        if (ticketAutorizadoRepository != null) {
            ids.addAll(ticketAutorizadoRepository.ticketsDe(u.getIdUser()));
        }
        if (accesoConfidencialRepository != null) {
            ids.addAll(accesoConfidencialRepository.vigentesDe(u.getIdUser(), java.time.OffsetDateTime.now()));
        }
        return ids;
    }

    public static boolean esConfidencial(Ticket t) {
        return t != null && Boolean.TRUE.equals(t.getConfidencialTicket());
    }

    private java.util.Set<UUID> areasDelJefe(User u) {
        if (jefaturaService != null) {
            return jefaturaService.areasACargo(u);
        }
        return u.getArea() == null ? java.util.Set.of() : java.util.Set.of(u.getArea().getIdArea());
    }

    public boolean puedeLeer(Ticket t, User u) {
        if (t == null || u == null) {
            return false;
        }
        return puedeLeer(t, alcanceDe(u));
    }

    /** Lo mismo con un alcance ya calculado (listas: una sola consulta por persona). */
    public boolean puedeLeer(Ticket t, AlcanceTickets alcance) {
        UUID solicitante = t.getUserTicket() == null ? null : t.getUserTicket().getIdUser();
        UUID agente = t.getAgentTicket() == null ? null : t.getAgentTicket().getIdUser();
        UUID atiende = t.getAreaDestinoTicket() == null ? null : t.getAreaDestinoTicket().getIdArea();
        if (esConfidencial(t)) {
            return alcance.veConfidencial(t.getIdTicket(), solicitante, agente, atiende,
                    Boolean.TRUE.equals(t.getConfidencialAreaTicket()));
        }
        return alcance.incluye(solicitante, agente, atiende,
                t.getAreaTicket() == null ? null : t.getAreaTicket().getIdArea());
    }

    /**
     * Responder en el chat: quien puede escribir y, además, el jefe que sigue un
     * ticket pedido por su gente (comenta, pero no lo gestiona).
     */
    public boolean puedeComentar(Ticket t, User u) {
        if (puedeEscribir(t, u)) {
            return true;
        }
        // El seguimiento del jefe no alcanza a los confidenciales.
        return !esConfidencial(t) && u != null && u.getRolUser() != null
                && com.mesaayuda.security.Roles.esJefe(u.getRolUser().getNameRole())
                && t != null && t.getAreaTicket() != null
                && areasDelJefe(u).contains(t.getAreaTicket().getIdArea());
    }

    /** Puede ESCRIBIR: el titular, el agente asignado o un administrador. */
    public boolean puedeEscribir(Ticket t, User u) {
        if (esConfidencial(t)) {
            // En un confidencial, el súper admin no escribe: como mucho lo lee
            // con un acceso de emergencia aprobado.
            return esPropietario(t, u) || esAgenteAsignado(t, u) || esJefeDelArea(t, u);
        }
        return esPropietario(t, u) || esAgenteAsignado(t, u) || esPersonalAdministrador(u) || esJefeDelArea(t, u);
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
            // Solo al súper admin y al auditor se les dice que es confidencial (y
            // pueden pedir acceso). A cualquier otro, un 403 normal: no tiene por
            // qué saber que ese ticket existe ni que es confidencial.
            if (esConfidencial(t) && actual != null && actual.getRolUser() != null
                    && com.mesaayuda.security.Roles.veTodo(actual.getRolUser().getNameRole())) {
                log.warn("Acceso denegado: {} intentó {} del ticket confidencial #{}.",
                        actual != null ? actual.getCorporateEmailUser() : "desconocido", queSeHacia, id);
                throw new com.mesaayuda.exception.TicketConfidencialException();
            }
            log.warn("Acceso denegado: {} intentó {} del ticket #{}.",
                    actual != null ? actual.getCorporateEmailUser() : "desconocido", queSeHacia, id);
            throw new AccessDeniedException("No tiene acceso a este ticket");
        }
    }
}