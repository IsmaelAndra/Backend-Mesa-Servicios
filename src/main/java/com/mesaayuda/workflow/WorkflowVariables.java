package com.mesaayuda.workflow;

import com.mesaayuda.entity.Ticket;
import com.mesaayuda.entity.TicketSla;
import com.mesaayuda.entity.User;
import com.mesaayuda.repository.TicketRepository;
import com.mesaayuda.repository.TicketSlaRepository;
import com.mesaayuda.repository.UserRepository;
import com.mesaayuda.service.NombreVisible;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Variables "vivas" de un workflow: {@code ticket.*} y {@code usuario.*}.
 *
 * <p>Antes las variables eran solo la foto del evento que disparó el workflow.
 * Tras un WAIT de 24 horas, "¿el ticket está cerrado?" miraba el estado que tenía
 * el ticket al crearse, no el actual. Estas dos familias se leen de la base en el
 * momento en que un nodo las pide, así que siempre están al día.
 *
 * <p>Lo demás sigue en el mapa de variables de la ejecución: {@code evento.*} (la
 * foto del disparador), {@code nodos.<id>.*} (lo que deja cada nodo) y
 * {@code ejecucion.*}.
 *
 * <p>Las claves son un catálogo cerrado, el mismo que ve el constructor en su
 * selector. No se navega por la entidad con reflexión: un workflow no tiene por
 * qué poder leer el hash de la contraseña de un usuario solo porque el campo
 * existe.
 */
@Component
public class WorkflowVariables {

    /** Lo que el constructor ofrece en el selector de variables, con su descripción. */
    public static final Map<String, String> CATALOGO_TICKET = ordenado(
            "ticket.id", "Identificador interno del ticket (para acciones y enlaces)",
            "ticket.numero", "Número del ticket, el que ve la gente (#43)",
            "ticket.titulo", "Título",
            "ticket.descripcion", "Descripción",
            "ticket.estado", "Estado (NUEVO, ASIGNADO, EN_PROCESO, RESUELTO, CERRADO...)",
            "ticket.prioridad", "Prioridad",
            "ticket.categoria", "Categoría",
            "ticket.tipo", "Tipo de ticket",
            "ticket.area", "Área",
            "ticket.equipo", "Equipo asignado",
            "ticket.sede", "Sede",
            "ticket.agente", "Nombre del agente asignado",
            "ticket.agenteId", "Id del agente asignado",
            "ticket.agenteCorreo", "Correo del agente asignado",
            "ticket.solicitante", "Nombre del solicitante",
            "ticket.solicitanteId", "Id del solicitante",
            "ticket.solicitanteCorreo", "Correo del solicitante",
            "ticket.creado", "Fecha de creación",
            "ticket.actualizado", "Última modificación",
            "ticket.cerrado", "Fecha de cierre",
            "ticket.sla.politica", "Política de SLA aplicada",
            "ticket.sla.venceRespuesta", "Vencimiento de la primera respuesta",
            "ticket.sla.venceResolucion", "Vencimiento de la resolución",
            "ticket.sla.incumplido", "Si el SLA ya se incumplió (true/false)");

    public static final Map<String, String> CATALOGO_USUARIO = ordenado(
            "usuario.id", "Id del usuario",
            "usuario.nombre", "Nombre",
            "usuario.correo", "Correo corporativo",
            "usuario.rol", "Rol",
            "usuario.estado", "Estado (ACTIVE/INACTIVE)",
            "usuario.area", "Área");

    private final TicketRepository ticketRepository;
    private final TicketSlaRepository ticketSlaRepository;
    private final UserRepository userRepository;

    public WorkflowVariables(TicketRepository ticketRepository,
                             TicketSlaRepository ticketSlaRepository,
                             UserRepository userRepository) {
        this.ticketRepository = ticketRepository;
        this.ticketSlaRepository = ticketSlaRepository;
        this.userRepository = userRepository;
    }

    /** Conecta el contexto con este resolvedor para las familias ticket.* y usuario.*. */
    public void vincular(WorkflowContext contexto) {
        contexto.setResolvedor(ruta -> resolver(contexto, ruta));
    }

    Object resolver(WorkflowContext contexto, String ruta) {
        if (ruta.startsWith("ticket.")) {
            UUID id = uuid(contexto.leerGuardada("ticketId"), contexto.leerGuardada("evento.ticketId"));
            if (id == null) {
                return null;
            }
            Ticket t = ticketRepository.findById(id).orElse(null);
            return t == null ? null : campoTicket(t, ruta.substring("ticket.".length()));
        }
        if (ruta.startsWith("usuario.")) {
            UUID id = uuid(contexto.leerGuardada("usuarioId"), contexto.leerGuardada("evento.usuarioId"));
            if (id == null) {
                return null;
            }
            User u = userRepository.findById(id).orElse(null);
            return u == null ? null : campoUsuario(u, ruta.substring("usuario.".length()));
        }
        return null;
    }

    private Object campoTicket(Ticket t, String campo) {
        if (campo.startsWith("sla.")) {
            TicketSla sla = ticketSlaRepository.findByTicketIdTicket(t.getIdTicket()).orElse(null);
            if (sla == null) {
                return null;
            }
            return switch (campo) {
                case "sla.politica" -> sla.getSlaPolicy() == null ? null : sla.getSlaPolicy().getNameSlaPolicy();
                case "sla.venceRespuesta" -> sla.getResponseDueAt();
                case "sla.venceResolucion" -> sla.getResolutionDueAt();
                case "sla.incumplido" -> Boolean.TRUE.equals(sla.getResponseBreached())
                        || Boolean.TRUE.equals(sla.getResolutionBreached());
                default -> null;
            };
        }
        return switch (campo) {
            case "id" -> t.getIdTicket();
            case "numero" -> t.getNumberTicket();
            case "titulo" -> t.getTitleTicket();
            case "descripcion" -> t.getDescriptionTicket();
            case "estado" -> t.getStatusTicket() == null ? null : t.getStatusTicket().getNameStatus();
            case "prioridad" -> t.getPriorityTicket() == null ? null : t.getPriorityTicket().getNamePriority();
            case "categoria" -> t.getCategoryTicket() == null ? null : t.getCategoryTicket().getNameCategory();
            case "tipo" -> t.getTypeTicket() == null ? null : t.getTypeTicket().getNameTicketType();
            case "area" -> t.getAreaTicket() == null ? null : t.getAreaTicket().getNameArea();
            case "equipo" -> t.getTeamTicket() == null ? null : t.getTeamTicket().getNameTeam();
            case "sede" -> t.getSucursalTicket() == null ? null : t.getSucursalTicket().getNameSucursal();
            case "agente" -> t.getAgentTicket() == null ? null : NombreVisible.de(t.getAgentTicket());
            case "agenteId" -> t.getAgentTicket() == null ? null : t.getAgentTicket().getIdUser();
            case "agenteCorreo" -> t.getAgentTicket() == null ? null : t.getAgentTicket().getCorporateEmailUser();
            case "solicitante" -> t.getUserTicket() == null ? null : NombreVisible.de(t.getUserTicket());
            case "solicitanteId" -> t.getUserTicket() == null ? null : t.getUserTicket().getIdUser();
            case "solicitanteCorreo" -> t.getUserTicket() == null ? null : t.getUserTicket().getCorporateEmailUser();
            case "creado" -> t.getCreatedAtTicket();
            case "actualizado" -> t.getUpdatedAtTicket();
            case "cerrado" -> t.getClosingDateTicket();
            default -> null;
        };
    }

    private Object campoUsuario(User u, String campo) {
        return switch (campo) {
            case "id" -> u.getIdUser();
            case "nombre" -> NombreVisible.de(u);
            case "correo" -> u.getCorporateEmailUser();
            case "rol" -> u.getRolUser() == null ? null : u.getRolUser().getNameRole();
            case "estado" -> u.getStatusUser();
            case "area" -> u.getArea() == null ? null : u.getArea().getNameArea();
            default -> null;
        };
    }

    private static UUID uuid(Object... candidatos) {
        for (Object c : candidatos) {
            if (c instanceof UUID u) {
                return u;
            }
            if (c != null) {
                try {
                    return UUID.fromString(String.valueOf(c).trim());
                } catch (IllegalArgumentException e) {
                    // Se prueba con el siguiente candidato.
                }
            }
        }
        return null;
    }

    private static Map<String, String> ordenado(String... pares) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pares.length; i += 2) {
            m.put(pares[i], pares[i + 1]);
        }
        return java.util.Collections.unmodifiableMap(m);
    }

    /** Todas las claves vivas, para validar que un IF no apunta a un campo inexistente. */
    public static List<String> clavesVivas() {
        List<String> claves = new java.util.ArrayList<>(CATALOGO_TICKET.keySet());
        claves.addAll(CATALOGO_USUARIO.keySet());
        return claves;
    }
}
