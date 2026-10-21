package com.mesaayuda.service;

import com.mesaayuda.dto.*;
import com.mesaayuda.entity.Sucursal;
import com.mesaayuda.entity.*;
import com.mesaayuda.exception.BadRequestException;
import com.mesaayuda.exception.ResourceNotFoundException;
import com.mesaayuda.mapper.AnswerMapper;
import com.mesaayuda.mapper.TicketMapper;
import com.mesaayuda.repository.SucursalRepository;
import com.mesaayuda.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.persistence.criteria.Predicate;
import java.io.IOException;
import java.io.InputStream;
import com.mesaayuda.config.UploadZones;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class TicketService {

    private static final Logger log = LoggerFactory.getLogger(TicketService.class);

    @Autowired
    private TicketRepository ticketRepository;
    @Autowired private SucursalRepository sucursalRepository;

    @Autowired
    private AnswerReadRepository answerReadRepository;

    /** Quien puede ver los acuses de lectura, segun la configuracion del admin. */
    @Autowired
    private ChatVisibilityPolicy chatVisibilityPolicy;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private StatusRepository statusRepository;

    @Autowired
    private PriorityRepository priorityRepository;

    @Autowired
    private AnswerRepository answerRepository;

    @Autowired
    private AttachRepository attachRepository;

    @Autowired
    private TicketTypeRepository ticketTypeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoutingService routingService;

    @Autowired
    private com.mesaayuda.repository.SupportTeamRepository supportTeamRepository;

    @Autowired
    private SlaService slaService;

    @Autowired
    private com.mesaayuda.repository.TicketSlaRepository ticketSlaRepository;

    /**
     * Engancha los cambios de ticket con el motor de workflows.
     *
     * <p>El publicador espera al commit antes de encolar nada, así que aquí no
     * hay ningún coste para la petición más allá de registrar un callback.
     */
    @Autowired
    private com.mesaayuda.workflow.WorkflowEventPublisher workflowEventPublisher;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private AuditService auditService;

    @Autowired
    private SseEventBroker sseEventBroker;

    @Autowired
    private PresenceService presenceService;

    @Autowired
    private UserService userService;

    /** Definición única de quién puede leer y escribir en un ticket. */
    @Autowired
    private TicketAccessPolicy accessPolicy;

    @Autowired
    private TicketMapper ticketMapper;

    @Autowired
    private AnswerMapper answerMapper;

    @Value("${app.uploads.dir}")
    private String uploadDir;

    // ============================ CREACIÓN ============================

    /**
     * Sede del ticket: la indicada en el formulario y, si no viene, la del
 * solicitante. Una sede borrada o desactivada se ignora en vez de fallar, para
 * que un ticket se pueda abrir igual y solo quede sin sede.
 */
private Sucursal sucursalDelTicket(UUID sucursalId, User requester) {
    if (sucursalId != null) {
        Sucursal elegida = sucursalRepository.findById(sucursalId)
                .filter(s -> !s.getDeletedSucursal())
                .orElse(null);
        if (elegida != null) {
            return elegida;
        }
    }
    return requester.getSucursal();
}

public TicketResponse create(TicketRequest request) {
        User creador = userService.getCurrentUser();
        User requester = solicitanteDe(request, creador);

        Category category = (request.categoryId() != null)
                ? categoryRepository.findById(request.categoryId())
                        .filter(c -> !c.getDeletedCategory())
                        .orElseThrow(() -> new ResourceNotFoundException("Categoría", "id", request.categoryId()))
                : categoryRepository.findFirstByDeletedCategoryFalseOrderByCreatedAtCategoryAsc()
                        .orElseThrow(() -> new BadRequestException("No hay categorías disponibles"));

        TicketType type = (request.typeId() != null)
                ? ticketTypeRepository.findById(request.typeId())
                        .filter(t -> !t.getDeletedTicketType())
                        .orElseThrow(() -> new ResourceNotFoundException("Tipo de ticket", "id", request.typeId()))
                : ticketTypeRepository.findTopByDeletedTicketTypeFalseOrderByCreatedAtTicketTypeAsc().orElse(null);

        Status newStatus = statusByName("NUEVO");
        Priority priority = (request.priorityId() != null)
                ? priorityRepository.findById(request.priorityId())
                        .orElseThrow(() -> new ResourceNotFoundException("Prioridad", "id", request.priorityId()))
                : resolvePriority(category);

        Ticket ticket = new Ticket();
        ticket.setTitleTicket(request.title());
        ticket.setDescriptionTicket(request.description());
        ticket.setUserTicket(requester);
        ticket.setAreaTicket(requester.getArea());
        // Si no se indica sede, el ticket arranca en la del solicitante. El
        // formulario permite cambiarla porque el caso puede corresponder a otra
        // oficina, pero casi siempre es la misma.
        ticket.setSucursalTicket(sucursalDelTicket(request.sucursalId(), requester));
        ticket.setCategoryTicket(category);
        ticket.setTypeTicket(type);
        ticket.setStatusTicket(newStatus);
        ticket.setPriorityTicket(priority);
        ticket.setPriorityManualTicket(request.priorityId() != null && Boolean.TRUE.equals(request.priorityManual()));
        ticket.setTeamTicket(category.getTeamCategory());
        ticket.setDeletedTicket(false);
        ticket = ticketRepository.save(ticket);
        // El número (#43) lo pone la secuencia al insertar. Con id uuid el INSERT
        // se aplaza hasta el commit, y el aviso "Ticket #[id] creado" salía sin
        // número: se fuerza aquí para que Hibernate lo lea antes de notificar.
        ticketRepository.flush();

        User routedAgent = routingService.routeTicket(category);
        if (routedAgent != null) {
            ticket = assignAgent(ticket, routedAgent, false);
        }

        slaService.applySlaToTicket(ticket);
        auditService.record(ticket, "CREATE", null, ticket.getTitleTicket(),
                requester == creador
                        ? "Ticket creado por " + creador.getCorporateEmailUser()
                        : "Ticket creado por " + creador.getCorporateEmailUser()
                                + " a nombre de " + requester.getCorporateEmailUser());
        notificationService.notifyTicketCreated(ticket);
        if (ticket.getAgentTicket() != null) {
            notificationService.notifyTicketAssigned(ticket);
        }

        publishSse("TICKET_CREATED", ticket, creador.getIdUser(), creador.getCorporateEmailUser());
        // La automatización se encola despues del commit, no aqui: si se
        // ejecutara ahora, el worker podria tomar la ejecucion antes de que
        // esta transaccion confirme y leeria un ticket que todavia no existe.
        workflowEventPublisher.ticketCreado(ticket);

        log.info("Ticket #{} creado por {}", ticket.getIdTicket(), requester.getCorporateEmailUser());
        return ticketMapper.toResponse(ticket);
      }

    /**
     * Quién figura como solicitante. Solo el personal (agente, administrador)
     * puede abrir un ticket a nombre de otra persona, y esa persona tiene que
     * existir y estar activa. Un solicitante que mande requesterId de otro
     * recibe 403: no puede crear casos en nombre ajeno.
     */
    private User solicitanteDe(TicketRequest request, User creador) {
        UUID id = request.requesterId();
        if (id == null || id.equals(creador.getIdUser())) {
            return creador;
        }
        if (!esPersonal(creador)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Solo el personal puede crear tickets a nombre de otra persona");
        }
        return userRepository.findById(id)
                .filter(u -> !Boolean.TRUE.equals(u.getDeletedUser()))
                .filter(this::esActivo)
                .orElseThrow(() -> new BadRequestException("El solicitante no existe o no está activo"));
    }

    private Priority resolvePriority(Category category) {
        int level = category.getTeamCategory() != null
                ? (category.getTeamCategory().getIdTeam() != null ? 2 : 1) : 1;
        return priorityRepository.findByLevelPriority(level)
                .orElse(priorityRepository.findByLevelPriority(1)
                        .orElseThrow(() -> new BadRequestException("No hay prioridades disponibles")));
    }

    // ============================ CONSULTA ============================

    @Transactional(readOnly = true)
    public Page<TicketResponse> search(String status, String priority, String category,
                                       UUID areaId, UUID agentId, UUID requesterId,
                                       String keyword, UUID sucursalId, int page, int size) {
        User actual = userService.getCurrentUser();

        // Un solicitante solo puede listar sus propios tickets. Antes el filtro
        // requesterId venía del cliente sin comprobar nada, así que bastaba
        // con ?requesterId=<otro id> para ver los tickets de otra persona.
        // El personal conserva el filtro libre: el kanban lo necesita.
        UUID solicitanteEfectivo = esPersonal(actual) ? requesterId : actual.getIdUser();
        // Solo ADMIN y SUPER_ADMIN ven todos los tickets. Un agente ve los que
        // abrió y los que tiene asignados: antes veía los de todo el mundo en la
        // lista de tickets, aunque no tuviera nada que ver con ellos.
        UUID agenteRestringido = esPersonal(actual) && !esPersonalAdministrador(actual)
                ? actual.getIdUser() : null;

        Specification<Ticket> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("deletedTicket")));
            if (agenteRestringido != null) {
                predicates.add(cb.or(
                        cb.equal(root.get("userTicket").get("idUser"), agenteRestringido),
                        cb.equal(root.get("agentTicket").get("idUser"), agenteRestringido)));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("statusTicket").get("nameStatus"), status.toUpperCase()));
            }
            if (priority != null && !priority.isBlank()) {
                predicates.add(cb.equal(root.get("priorityTicket").get("namePriority"), priority.toUpperCase()));
            }
            if (category != null && !category.isBlank()) {
                predicates.add(cb.equal(root.get("categoryTicket").get("nameCategory"), category));
            }
            if (areaId != null) {
                predicates.add(cb.equal(root.get("areaTicket").get("idArea"), areaId));
            }
            if (sucursalId != null) {
                predicates.add(cb.equal(root.get("sucursalTicket").get("idSucursal"), sucursalId));
            }
            if (agentId != null) {
                predicates.add(cb.equal(root.get("agentTicket").get("idUser"), agentId));
            }
            if (solicitanteEfectivo != null) {
                predicates.add(cb.equal(root.get("userTicket").get("idUser"), solicitanteEfectivo));
            }
            if (keyword != null && !keyword.isBlank()) {
                List<Predicate> coincidencias = new ArrayList<>();
                coincidencias.add(cb.like(cb.lower(root.get("titleTicket")), "%" + keyword.toLowerCase() + "%"));
                coincidencias.add(cb.like(cb.lower(root.get("descriptionTicket")), "%" + keyword.toLowerCase() + "%"));
                // "43" o "#43": la gente busca el ticket por su número.
                Long numero = numeroDeTicket(keyword);
                if (numero != null) {
                    coincidencias.add(cb.equal(root.get("numberTicket"), numero));
                }
                predicates.add(cb.or(coincidencias.toArray(new Predicate[0])));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAtTicket"));
        return conSesion(ticketRepository.findAll(spec, pageable).map(ticketMapper::toResponse));
    }

    /**
     * Añade el estado de sesión del solicitante a cada ticket, resolviendo todos los
     * usuarios de la página en una sola consulta.
     */
    private Page<TicketResponse> conSesion(Page<TicketResponse> pagina) {
        List<TicketResponse> tickets = pagina.getContent();
        if (tickets.isEmpty()) {
            return pagina;
        }
        Map<UUID, String> estados = presenceService.estadosDe(
                tickets.stream().filter(Objects::nonNull).map(TicketResponse::userId)
                        .filter(Objects::nonNull).toList());
        List<TicketResponse> conEstado = tickets.stream()
                .map(t -> {
                    if (t == null) {
                        return null;
                    }
                    String estado = t.userId() == null ? null : estados.get(t.userId());
                    return estado == null ? t : t.withUserSessionState(estado);
                })
                .toList();
        return new PageImpl<>(conEstado, pagina.getPageable(), pagina.getTotalElements());
    }

    private List<TicketResponse> conSesion(List<TicketResponse> tickets) {
        if (tickets == null || tickets.isEmpty()) {
            return tickets;
        }
        Map<UUID, String> estados = presenceService.estadosDe(
                tickets.stream().filter(Objects::nonNull).map(TicketResponse::userId)
                        .filter(Objects::nonNull).toList());
        return tickets.stream()
                .map(t -> {
                    if (t == null) {
                        return null;
                    }
                    String estado = t.userId() == null ? null : estados.get(t.userId());
                    return estado == null ? t : t.withUserSessionState(estado);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public TicketResponse findById(UUID id) {
        TicketResponse ticket = ticketMapper.toResponse(getTicketLegible(id));
        if (ticket == null) {
            return null;
        }
        // La cabecera de la ficha de detalle muestra el punto de sesión del solicitante,
        // y este endpoint es el que la alimenta.
        return ticket.withUserSessionState(presenceService.estadoDe(ticket.userId()));
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> myTickets() {
        User current = userService.getCurrentUser();
        return conSesion(ticketRepository
                .findByUserTicketIdUserAndDeletedTicketFalseOrderByCreatedAtTicketDesc(current.getIdUser())
                .stream().map(ticketMapper::toResponse).toList());
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> assignedTickets() {
        User current = userService.getCurrentUser();
        return conSesion(ticketRepository
                .findByAgentTicketIdUserAndDeletedTicketFalseOrderByCreatedAtTicketDesc(current.getIdUser())
                .stream().map(ticketMapper::toResponse).toList());
    }

// ============================ OPERACIONES ============================

  /**
   * Quién puede asignar o reasignar este ticket.
   *
   * <p>Un administrador puede hacerlo con cualquiera. Un agente solo puede tomar
   * uno que siga sin agente, que es el caso normal del trabajo en la cola, o
   * mantener el suyo. Reasignar el ticket de otra persona es una decisión de
   * supervision y se rechaza con 403, no con 400: no es una peticion mal
   * formada, es una peticion sin permiso.
   */
  private void exigirPuedeAsignar(Ticket ticket, User actor) {
      if (accessPolicy.esPersonalAdministrador(actor)) {
          return;
      }
      boolean sinAgente = ticket.getAgentTicket() == null;
      boolean esElPropio = accessPolicy.esAgenteAsignado(ticket, actor);
      if (accessPolicy.esPersonal(actor) && (sinAgente || esElPropio)) {
          return;
      }
      log.warn("Acceso denegado: {} intentó asignar el ticket #{}.", actor.getCorporateEmailUser(),
              ticket.getIdTicket());
      throw new AccessDeniedException("No tiene permisos para asignar este ticket");
  }

  public TicketResponse assign(UUID ticketId, AssignRequest request) {
          User actor = userService.getCurrentUser();
          // Antes se usaba getTicket(), que solo comprueba que el ticket exista.
          // Eso dejaba a cualquier agente reasignar cualquier ticket de cualquier
          // otro, generando la cadena de avisos, correo y evento de SSE.
          //
          // La regla que se aplica es la de una mesa de servicios: un agente
          // puede tomar un ticket SIN asignar de la cola, que es como el trabajo
          // llega al kanban, pero mover un ticket que ya tiene agente es
          // supervision y corresponde a un administrador. Sin esta distincion,
          // aplicar puedeEscribir a pelo dejaria la cola sin poder reclamar.
          Ticket ticket = getTicket(ticketId);
          exigirPuedeAsignar(ticket, actor);

          User newAgent = request.agentId() != null
                  ? userRepository.findById(request.agentId())
                          .filter(u -> !u.getDeletedUser() && "AGENT".equals(u.getRolUser().getNameRole()))
                          .orElseThrow(() -> new ResourceNotFoundException("Agente", "id", request.agentId()))
                  : routingService.routeTicket(ticket.getCategoryTicket());

        if (newAgent == null && request.teamId() != null) {
            throw new BadRequestException("No hay agentes disponibles en el equipo seleccionado");
        }
        if (newAgent == null) {
            throw new BadRequestException("No se pudo determinar el agente de asignación");
        }

        String oldAgent = ticket.getAgentTicket() != null
                ? ticket.getAgentTicket().getCorporateEmailUser() : "sin asignar";
        ticket = assignAgent(ticket, newAgent, true);
        auditService.record(ticket, "ASSIGN", oldAgent, newAgent.getCorporateEmailUser(), "Asignación manual/automática");
        notificationService.notifyTicketAssigned(ticket);
publishSse("TICKET_ASSIGNED", ticket, actor.getIdUser(), actor.getCorporateEmailUser());
          workflowEventPublisher.ticketAsignado(ticket);
          return ticketMapper.toResponse(ticket);
      }

/**
   * Pasa el ticket a un equipo y elige agente dentro de él con el método del
   * equipo (rotación, menos asignado o manual).
   *
   * <p>Antes no existía: la acción "asignar equipo" de los workflows llamaba a
   * {@link #assign} con el id del equipo, que lo ignoraba y enrutaba por la
   * categoría, así que el ticket acababa en el equipo de siempre. Si el equipo
   * está en modo manual o no tiene a nadie disponible, el ticket queda en su cola
   * sin agente, que es exactamente lo que significa "asignar al equipo".
   */
  @Transactional
  public TicketResponse assignTeam(UUID ticketId, UUID teamId) {
      Ticket ticket = getTicketGestionable(ticketId, "asignar a un equipo");
      User actor = userService.getCurrentUser();
      com.mesaayuda.entity.SupportTeam equipo = supportTeamRepository.findById(teamId)
              .filter(t -> Boolean.TRUE.equals(t.getActiveTeam()))
              .orElseThrow(() -> new BadRequestException("El equipo " + teamId + " no existe o está inactivo."));

      String equipoAnterior = ticket.getTeamTicket() == null ? "sin equipo" : ticket.getTeamTicket().getNameTeam();
      UUID agenteAnterior = ticket.getAgentTicket() == null ? null : ticket.getAgentTicket().getIdUser();
      ticket.setTeamTicket(equipo);
      User agente = routingService.routeTeam(equipo);
      if (agente != null) {
          ticket = assignAgent(ticket, agente, true);
      } else {
          // El agente anterior era de otro equipo: el ticket pasa a la cola del nuevo.
          ticket.setAgentTicket(null);
          ticket = ticketRepository.save(ticket);
      }
      auditService.record(ticket, "TEAM_ASSIGN", equipoAnterior, equipo.getNameTeam(),
              agente == null ? "Sin agente disponible: queda en la cola del equipo"
                      : "Agente: " + agente.getCorporateEmailUser());
      if (agente != null && !agente.getIdUser().equals(agenteAnterior)) {
          notificationService.notifyTicketAssigned(ticket);
          publishSse("TICKET_ASSIGNED", ticket, actor.getIdUser(), actor.getCorporateEmailUser());
          workflowEventPublisher.ticketAsignado(ticket);
      } else {
          publishSse("TICKET_UPDATED", ticket, actor.getIdUser(), actor.getCorporateEmailUser());
          workflowEventPublisher.ticketActualizado(ticket);
      }
      log.info("Ticket #{} pasa al equipo {} (agente: {})", ticketId, equipo.getNameTeam(),
              agente == null ? "ninguno" : agente.getCorporateEmailUser());
      return ticketMapper.toResponse(ticket);
  }

public TicketResponse changeStatus(UUID ticketId, StatusChangeRequest request) {
          // Antes solo se comprobaba "¿es personal?", sin mirar nada del ticket:
          // cualquier agente cerraba, reabria o resolvia tickets de otros, lo que
          // ademas rompia el SLA y disparaba avisos a las personas equivocadas.
          // getTicketGestionable exige agente asignado o administrador.
          Ticket ticket = getTicketGestionable(ticketId, "cambiar el estado");
          User current = userService.getCurrentUser();

          Status newStatus = statusByName(request.statusName());
          String oldStatus = ticket.getStatusTicket().getNameStatus();

        if (oldStatus.equals("CERRADO") && !newStatus.getNameStatus().equals("REABIERTO")) {
            throw new BadRequestException("Un ticket cerrado solo puede reabrirse");
        }

        ticket.setStatusTicket(newStatus);

        switch (newStatus.getNameStatus()) {
            case "RESUELTO" -> {
                slaService.markResponseCompleted(ticket);
                slaService.markResolutionCompleted(ticket);
                notificationService.notifyTicketResolved(ticket);
            }
            case "CERRADO" -> {
                ticket.setClosingDateTicket(OffsetDateTime.now());
                notificationService.notifyTicketClosed(ticket);
            }
            case "REABIERTO" -> ticket.setClosingDateTicket(null);
            default -> { }
        }

        ticket = ticketRepository.save(ticket);
        auditService.record(ticket, "STATUS_CHANGE", oldStatus, newStatus.getNameStatus(), null);
publishSse("TICKET_STATUS_CHANGED", ticket, current.getIdUser(), current.getCorporateEmailUser());
          log.info("Ticket #{} cambió de estado {} -> {}", ticketId, oldStatus, newStatus.getNameStatus());
          workflowEventPublisher.ticketCambioEstado(ticket, oldStatus);
          return ticketMapper.toResponse(ticket);
    }

    // ============================ RESPUESTAS ============================

    public AnswerResponse addAnswer(UUID ticketId, AnswerRequest request) {
        Ticket ticket = getTicketEscribible(ticketId);
        User current = userService.getCurrentUser();

        boolean isStaff = esPersonalAdministrador(current) || esAgenteAsignado(ticket, current);

        boolean internal = Boolean.TRUE.equals(request.internal()) && isStaff;

        Answer answer = new Answer();
        answer.setMessageAnswer(request.message());
        answer.setInternalAnswer(internal);
        answer.setTicketAnswer(ticket);
        answer.setUserAnswer(current);
        answer.setDeletedAnswer(false);
        answer = answerRepository.save(answer);

        slaService.markResponseCompleted(ticket);
        auditService.record(ticket, "ANSWER", null, request.message(), "Respuesta de " + current.getCorporateEmailUser());
        // El aviso va como CHAT_MENSAJE y no como TICKET_ACTUALIZADO: asi el panel de
        // notificaciones distingue "me contestaron en el hilo" de "movieron el
        // ticket" solo con el icono. Si no hay plantilla CHAT_MENSAJE en
        // NotificationService se usa el texto por defecto, sin degradar nada.
        String autorVisible = nombreVisible(current);
        User notified = current.getIdUser().equals(ticket.getUserTicket().getIdUser())
                ? ticket.getAgentTicket() : ticket.getUserTicket();
        // Una nota interna (mensaje privado) es solo para el personal. Antes el
        // aviso salía igual al solicitante, por correo y en la campana, con el
        // texto completo de la nota. Ahora va al agente asignado (si no es quien
        // escribe) y a los administradores, nunca al solicitante.
        if (internal) {
            User agente = ticket.getAgentTicket();
            notified = agente != null && !agente.getIdUser().equals(current.getIdUser()) ? agente : null;
        }
        if (notified != null) {
            notificationService.notifyChatMessage(ticket, notified, request.message(), autorVisible);
        }
        // Aparte del titular y del agente, se avisa a los administradores
        // activos que esten mirando la mesa. Antes solo se notificaba a esas dos
        // personas, asi que un ADMIN que no era el agente asignado no se
        // enteraba de la respuesta por el panel de notificaciones (aunque la
        // viera en la lista de tickets, porque los admin ven todos).
        Set<UUID> yaAvisados = new HashSet<>();
        if (notified != null) {
            yaAvisados.add(notified.getIdUser());
        }
        yaAvisados.add(current.getIdUser());
        for (String rol : List.of("ADMIN", "SUPER_ADMIN")) {
for (User admin : userRepository.findByRoleName(rol)) {
            if (admin.getDeletedUser() || !esActivo(admin)) {
                continue;
            }
            if (yaAvisados.add(admin.getIdUser())) {
                notificationService.notifyChatMessage(ticket, admin, request.message(), autorVisible);
                }
            }
        }

        publishSse("TICKET_ANSWERED", ticket, current.getIdUser(), current.getCorporateEmailUser());
        return answerMapper.toResponse(answer);
    }

    @Transactional
    public List<AnswerResponse> getAnswers(UUID ticketId) {
        Ticket ticket = getTicketLegible(ticketId);
        User current = userService.getCurrentUser();

        boolean isAgent = esAgenteAsignado(ticket, current);
        boolean isStaff = esPersonalAdministrador(current) || isAgent;

        List<Answer> visibles = answerRepository
                .findByTicketAnswerIdTicketAndDeletedAnswerFalseOrderByCreatedAtAnswerAsc(ticketId)
                .stream()
                .filter(a -> isStaff || !Boolean.TRUE.equals(a.getInternalAnswer()))
                .toList();

        if (visibles.isEmpty()) {
            return List.of();
        }

        // Abrir el hilo marca como leidas las respuestas que el usuario puede ver.
        // Se ignoran las propias: no se "lee" one's own message.
        List<UUID> ajenas = visibles.stream()
                .filter(a -> !a.getUserAnswer().getIdUser().equals(current.getIdUser()))
                .map(Answer::getIdAnswer)
                .toList();
        marcarLeidas(ajenas, current, ticketId);

        // Una sola consulta para saber quienes leyeron cada mensaje, en vez de
        // una por respuesta.
        Map<UUID, List<AnswerRead>> lecturas = new HashMap<>();
        for (AnswerRead r : answerReadRepository.findByAnswerIdAnswerIn(visibles.stream()
                .map(Answer::getIdAnswer).toList())) {
            lecturas.computeIfAbsent(r.getAnswer().getIdAnswer(), k -> new ArrayList<>()).add(r);
        }

        Map<UUID, String> sesiones = estadosDeAutores(visibles);

        return visibles.stream()
                .map(a -> toAnswerResponse(a, current,
                        lecturas.getOrDefault(a.getIdAnswer(), List.of()), sesiones, Set.of()))
                .toList();
    }

    /**
     * Inserta las marcas de lectura que falten, ignorando duplicados.
     * <p>
     * Cuando hay marcas nuevas avisa por SSE a quien escribio esos mensajes, para
     * que su doble check se ponga azul sin tener que recargar. El aviso se manda
     * despues del {@code saveAll} porque necesita los autores, y solo si se
     * guardo algo nuevo: si el usuario solo reabre un hilo que ya habia leido no
     * tiene sentido molestar al otro con un evento identico.
     */
    private Set<UUID> marcarLeidas(List<UUID> answerIds, User lector, UUID ticketId) {
        if (answerIds.isEmpty()) {
            return Set.of();
        }
        Set<UUID> yaLeidas = answerReadRepository.findByUserAndAnswers(lector.getIdUser(), answerIds)
                .stream()
                .map(r -> r.getAnswer().getIdAnswer())
                .collect(Collectors.toSet());

        List<AnswerRead> nuevas = new ArrayList<>();
        OffsetDateTime ahora = OffsetDateTime.now();
        for (Answer answer : answerRepository.findAllById(answerIds)) {
            if (yaLeidas.contains(answer.getIdAnswer())) {
                continue;
            }
            AnswerRead r = new AnswerRead();
            r.setAnswer(answer);
            r.setUser(lector);
            r.setReadAt(ahora);
            nuevas.add(r);
        }
        if (nuevas.isEmpty()) {
            return Set.of();
        }
        answerReadRepository.saveAll(nuevas);

        sseEventBroker.publishLectura(
                ticketId,
                lector.getIdUser(),
                nombreVisible(lector),
                nuevas.stream()
                        .map(r -> r.getAnswer().getUserAnswer().getIdUser())
                        .filter(id -> !id.equals(lector.getIdUser()))
                        .distinct()
                        .toList());
        // Las que estaban sin leer hasta ahora: el chat pinta ahí el separador
        // "Mensajes no leídos".
        return nuevas.stream().map(r -> r.getAnswer().getIdAnswer()).collect(Collectors.toSet());
    }

    /**
     * Página del hilo, del más reciente al más antiguo.
     * <p>
     * El chat abre por abajo: carga la última página y, cuando el usuario llega
     * arriba, pide la anterior. Por eso la consulta ordena al revés (lo más
     * reciente primero) y cada página se invierte antes de devolverla, para que
     * el orden en pantalla sea siempre de más antiguo a más reciente.
     * <p>
     * Las notas internas se filtran en la propia consulta y no después. Si se
     * filtraran sobre una página ya calculada, un solicitante recibiría páginas
     * a medias o vacías, y el total no quadraría con lo que ve.
     */
    @Transactional
    public AnswerPageResponse getAnswersPage(UUID ticketId, int page, int size) {
        Ticket ticket = getTicketLegible(ticketId);
        User current = userService.getCurrentUser();

        if (page < 0) {
            page = 0;
        }
        int tamano = Math.min(Math.max(size, 1), 100);

        boolean staff = esPersonalAdministrador(current) || esAgenteAsignado(ticket, current);

        Pageable pageable = PageRequest.of(page, tamano);
        List<Answer> ultimaPagina = staff
                ? answerRepository.findByTicketAnswerIdTicketAndDeletedAnswerFalseOrderByCreatedAtAnswerDescIdAnswerDesc(ticketId, pageable)
                : answerRepository
                        .findByTicketAnswerIdTicketAndInternalAnswerFalseAndDeletedAnswerFalseOrderByCreatedAtAnswerDescIdAnswerDesc(
                                ticketId, pageable);
        long total = staff
                ? answerRepository.countByTicketAnswerIdTicketAndDeletedAnswerFalse(ticketId)
                : answerRepository.countByTicketAnswerIdTicketAndInternalAnswerFalseAndDeletedAnswerFalse(ticketId);

        // Invertida para pintar de más antiguo a más reciente.
        List<Answer> visibles = new ArrayList<>(ultimaPagina);
        Collections.reverse(visibles);

        // Abrir el hilo marca como leídas las respuestas de ESTA página, que son
        // las que el usuario ha visto de verdad. Las de páginas que todavía no
        // ha cargado se marcan cuando lleguen.
        Set<UUID> sinLeerAlAbrir = Set.of();
        if (!visibles.isEmpty()) {
            List<UUID> ajenas = visibles.stream()
                    .filter(a -> !a.getUserAnswer().getIdUser().equals(current.getIdUser()))
                    .map(Answer::getIdAnswer)
                    .toList();
            sinLeerAlAbrir = marcarLeidas(ajenas, current, ticketId);
        }
        final Set<UUID> sinLeer = sinLeerAlAbrir;

        Map<UUID, List<AnswerRead>> lecturas = new HashMap<>();
        if (!visibles.isEmpty()) {
            for (AnswerRead r : answerReadRepository.findByAnswerIdAnswerIn(visibles.stream()
                    .map(Answer::getIdAnswer).toList())) {
                lecturas.computeIfAbsent(r.getAnswer().getIdAnswer(), k -> new ArrayList<>()).add(r);
            }
        }

        boolean hasMore = (long) (page + 1) * tamano < total;

        // Estado de sesión de cada autor de la página, en una sola consulta. Lo
        // necesita el chat para pintar el punto de presencia de quien escribe:
        // si se deja que el frontend lo deduzca de la lista de agentes y de la
        // ficha del solicitante, a un ADMIN o SUPER_ADMIN que responde le sale
        // "Desconectado" porque no está en ninguna de esas dos listas.
        Map<UUID, String> sesiones = estadosDeAutores(visibles);

        return new AnswerPageResponse(
                visibles.stream()
                        .map(a -> toAnswerResponse(a, current,
                                lecturas.getOrDefault(a.getIdAnswer(), List.of()), sesiones, sinLeer))
                        .toList(),
                page,
                tamano,
                hasMore,
                total);
    }

    /** Estados de sesión de los autores de estas respuestas, indexados por id de usuario. */
    private Map<UUID, String> estadosDeAutores(List<Answer> respuestas) {
        List<UUID> autores = respuestas.stream()
                .map(a -> a.getUserAnswer() != null ? a.getUserAnswer().getIdUser() : null)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return presenceService.estadosDe(autores);
    }

    /**
     * Datos de lectura de un mensaje para quien lo esta viendo.
     * <p>
     * El filtrado es aqui y no en el frontend a proposito: si el backend mandara
     * los nombres y el cliente decidiera si los muestra, cualquiera que abriera
     * las herramientas del navegador los veria igualmente.
     */
    private record DatosLectura(boolean leidoPorMi, Integer count, List<String> nombres) {
    }

    /**
     * Calcula los acuses de lectura respetando la visibilidad configurada. Si el
     * usuario actual no puede verlos, se omiten el recuento y los nombres: el
     * frontend entonces enseña solo el check simple.
     * <p>
     * Se conserva igualmente {@code leidoPorMi} porque es un hecho que el usuario
     * ya conoce de primera mano, no informacion sobre terceros: el Recuento de
     * quienes han leido un mensaje no lo revela quien lo leyo. Sin el, el chat del
     * solicitante no podria distinguir un mensaje entrante sin abrir de uno ya
     * leido y marcaria todos como "sin leer" para siempre.
     */
    private DatosLectura datosLectura(List<AnswerRead> lecturas, User lector) {
        boolean porMi = lecturas.stream()
                .anyMatch(r -> r.getUser().getIdUser().equals(lector.getIdUser()));
        if (!chatVisibilityPolicy.puedeVer(lector)) {
            return new DatosLectura(porMi, null, List.of());
        }
        if (!chatVisibilityPolicy.muestraNombres()) {
            // Se ve que lo ha leido alguien, pero no quien.
            return new DatosLectura(porMi, lecturas.size(), List.of());
        }
        return new DatosLectura(porMi, lecturas.size(), lecturas.stream()
                .map(r -> nombreVisible(r.getUser()))
                .toList());
    }

    private AnswerResponse toAnswerResponse(Answer a, User lector, List<AnswerRead> lecturas,
                                            Map<UUID, String> sesiones, Set<UUID> sinLeerAlAbrir) {
        DatosLectura d = datosLectura(lecturas, lector);
        UUID autorId = a.getUserAnswer() != null ? a.getUserAnswer().getIdUser() : null;
        return new AnswerResponse(
                a.getIdAnswer(),
                a.getMessageAnswer(),
                a.getInternalAnswer(),
                a.getUserAnswer().getCorporateEmailUser(),
                nombreVisible(a.getUserAnswer()),
                a.getUserAnswer().getPhotoUrl(),
                a.getCreatedAtAnswer(),
                d.leidoPorMi(),
                d.count(),
                d.nombres(),
                sesiones.getOrDefault(autorId, PresenceService.DESCONECTADO),
                a.getEditedAtAnswer(),
                sinLeerAlAbrir.contains(a.getIdAnswer()));
    }

    // ============================ EDITAR / ELIMINAR UNA RESPUESTA ============================

    /** Minutos durante los que el autor puede corregir o retirar su mensaje. */
    public static final int MINUTOS_EDICION = 15;

    /**
     * Corrige el texto de un mensaje propio.
     * <p>
     * Solo el autor y solo durante {@link #MINUTOS_EDICION} minutos: pasado ese
     * margen la otra parte ya puede haber actuado sobre lo que leyó. El texto
     * anterior queda en ticket_audit (append-only), así que editar nunca borra
     * lo que se dijo.
     */
    @Transactional
    public AnswerResponse editAnswer(UUID ticketId, UUID answerId, String nuevoTexto) {
        if (nuevoTexto == null || nuevoTexto.isBlank()) {
            throw new BadRequestException("El mensaje no puede quedar vacío");
        }
        Answer answer = respuestaPropiaEditable(ticketId, answerId);
        String anterior = answer.getMessageAnswer();
        answer.setMessageAnswer(nuevoTexto);
        answer.setEditedAtAnswer(OffsetDateTime.now());
        answer = answerRepository.save(answer);

        User current = userService.getCurrentUser();
        auditService.record(answer.getTicketAnswer(), "ANSWER_EDITED", anterior, nuevoTexto,
                "Respuesta editada por " + current.getCorporateEmailUser());
        publishSse("TICKET_ANSWER_UPDATED", answer.getTicketAnswer(), current.getIdUser(),
                current.getCorporateEmailUser());
        return answerMapper.toResponse(answer);
    }

    /** Retira un mensaje propio (borrado lógico) con las mismas reglas que editar. */
    @Transactional
    public void deleteAnswer(UUID ticketId, UUID answerId) {
        Answer answer = respuestaPropiaEditable(ticketId, answerId);
        answer.setDeletedAnswer(true);
        answerRepository.save(answer);

        User current = userService.getCurrentUser();
        auditService.record(answer.getTicketAnswer(), "ANSWER_DELETED", answer.getMessageAnswer(), null,
                "Respuesta eliminada por " + current.getCorporateEmailUser());
        publishSse("TICKET_ANSWER_UPDATED", answer.getTicketAnswer(), current.getIdUser(),
                current.getCorporateEmailUser());
    }

    private Answer respuestaPropiaEditable(UUID ticketId, UUID answerId) {
        getTicketEscribible(ticketId);
        User current = userService.getCurrentUser();
        Answer answer = answerRepository.findById(answerId)
                .filter(a -> !Boolean.TRUE.equals(a.getDeletedAnswer()))
                .filter(a -> a.getTicketAnswer().getIdTicket().equals(ticketId))
                .orElseThrow(() -> new ResourceNotFoundException("Respuesta", "id", answerId));
        if (!answer.getUserAnswer().getIdUser().equals(current.getIdUser())) {
            throw new AccessDeniedException("Solo quien escribió el mensaje puede cambiarlo");
        }
        if (answer.getCreatedAtAnswer() != null
                && answer.getCreatedAtAnswer().plusMinutes(MINUTOS_EDICION).isBefore(OffsetDateTime.now())) {
            throw new BadRequestException("Solo se puede editar o eliminar un mensaje durante los primeros "
                    + MINUTOS_EDICION + " minutos");
        }
        return answer;
    }

    // ============================ ADJUNTOS ============================

    public Attach uploadAttachment(UUID ticketId, UUID answerId, MultipartFile file) {
        // Misma regla que addAnswer: solo el titular, el agente asignado o un
        // administrador pueden adjuntar. Antes no se comprobaba nada y bastaba
        // con conocer el id de un ticket ajeno para escribir en su hilo.
        Ticket ticket = getTicketEscribible(ticketId);
        if (file.isEmpty()) {
            throw new BadRequestException("El archivo está vacío");
        }

        String originalName = file.getOriginalFilename();
        // El tipo lo decide la extensión, contrastada con el contenido; nunca el
        // Content-Type que manda el navegador, que el cliente elige libremente.
        String mime = validarTipoAdjunto(originalName, file);
        boolean duplicate = attachRepository
                .existsByTicketAttachIdTicketAndFileNameAttachAndDeletedAttachFalse(ticketId, originalName);
        if (duplicate) {
            throw new BadRequestException("Ya existe un archivo con el nombre '" + originalName
                    + "' en este ticket. Use un nombre diferente.");
        }

        Answer answer = null;
        if (answerId != null) {
            answer = answerRepository.findById(answerId)
                    .filter(a -> !a.getDeletedAnswer())
                    .filter(a -> a.getTicketAnswer().getIdTicket().equals(ticketId))
                    .orElseThrow(() -> new BadRequestException("La respuesta no pertenece a este ticket"));
        }

        try {
            // Los adjuntos van a su propia zona, que el handler de recursos estaticos NO
            // publica. Antes se guardaban junto a los logos y se servian como
            // estatico con /uploads/** en permitAll, de modo que cualquier persona
            // con la URL podia descargarlos sin tener permiso de lectura del ticket.
            Path dir = Paths.get(uploadDir).toAbsolutePath().normalize()
                    .resolve(UploadZones.ADJUNTOS);
            Files.createDirectories(dir);
            String uniqueName = UUID.randomUUID() + "_" + sanitize(originalName);
            Path target = dir.resolve(uniqueName);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            Attach attach = new Attach();
            attach.setFileNameAttach(originalName);
            // Solo el nombre unico, no la ruta absoluta del disco: esta columna se
            // expone en el DTO y una ruta del sistema no le aporta nada al cliente.
            attach.setStoragePathAttach(uniqueName);
            attach.setSizeFileAttach(file.getSize());
            attach.setMimeTypeAttach(mime);
            attach.setTicketAttach(ticket);
            attach.setAnswerAttach(answer);
            attach.setDeletedAttach(false);
            attach = attachRepository.save(attach);
            auditService.record(ticket, "ATTACH",
                    answer == null ? null : "Respuesta " + answer.getIdAnswer(),
                    originalName, "Archivo adjunto");
            User actor = userService.getCurrentUser();
            publishSse("TICKET_ATTACHMENT", ticket, actor.getIdUser(), actor.getCorporateEmailUser());
            return attach;
        } catch (IOException e) {
            // El detalle va al log y no al cliente: el mensaje de la excepción
            // suele llevar la ruta del disco donde se intentó escribir.
            log.error("No se pudo guardar el adjunto del ticket #{}: {}", ticketId, e.getMessage());
            throw new BadRequestException("No se pudo guardar el archivo");
        }
    }

    /**
     * Extensiones admitidas en los adjuntos y el tipo con el que se guardan.
     *
     * <p>Antes se aceptaba cualquier cosa (.exe, .bat, .html, .svg, .js) y se
     * guardaba el MIME que declaraba el navegador. El archivo no se ejecuta en el
     * servidor, pero un adjunto de ticket es la vía perfecta para que un agente
     * abra un ejecutable que le "manda el usuario".
     */
    private static final Map<String, String> TIPOS_ADJUNTO = Map.ofEntries(
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"),
            Map.entry("pdf", "application/pdf"),
            Map.entry("doc", "application/msword"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("xls", "application/vnd.ms-excel"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("ppt", "application/vnd.ms-powerpoint"),
            Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            Map.entry("txt", "text/plain"),
            Map.entry("csv", "text/csv"),
            Map.entry("zip", "application/zip"));

    /**
     * Comprueba que el adjunto sea de un tipo admitido y que el contenido
     * corresponda a la extensión. Devuelve el MIME con el que se guardará.
     */
    String validarTipoAdjunto(String nombre, MultipartFile file) {
        String limpio = nombre == null ? "" : nombre.trim().toLowerCase(Locale.ROOT);
        int punto = limpio.lastIndexOf('.');
        String ext = punto < 0 ? "" : limpio.substring(punto + 1);
        String mime = TIPOS_ADJUNTO.get(ext);
        if (mime == null) {
            throw new BadRequestException("Tipo de archivo no permitido. Se admiten: imágenes (PNG, JPG, GIF, "
                    + "WEBP), PDF, Word, Excel, PowerPoint, TXT, CSV y ZIP.");
        }

        byte[] cabecera = new byte[512];
        int leidos;
        try (InputStream in = file.getInputStream()) {
            leidos = in.readNBytes(cabecera, 0, cabecera.length);
        } catch (IOException e) {
            throw new BadRequestException("No se pudo leer el archivo");
        }
        if (!contenidoCoincide(ext, cabecera, leidos)) {
            log.warn("Adjunto rechazado: '{}' no tiene el contenido de un .{}", nombre, ext);
            throw new BadRequestException("El contenido del archivo no corresponde a su extensión (." + ext + ")");
        }
        return mime;
    }

    private static boolean contenidoCoincide(String ext, byte[] b, int n) {
        return switch (ext) {
            case "png" -> empiezaPor(b, n, 0x89, 'P', 'N', 'G');
            case "jpg", "jpeg" -> empiezaPor(b, n, 0xFF, 0xD8, 0xFF);
            case "gif" -> empiezaPor(b, n, 'G', 'I', 'F', '8');
            case "webp" -> empiezaPor(b, n, 'R', 'I', 'F', 'F') && n >= 12
                    && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
            case "pdf" -> empiezaPor(b, n, '%', 'P', 'D', 'F');
            // Los formatos de Office modernos y el ZIP son contenedores ZIP.
            case "docx", "xlsx", "pptx", "zip" -> empiezaPor(b, n, 'P', 'K', 0x03, 0x04);
            // Los de Office antiguos son contenedores OLE.
            case "doc", "xls", "ppt" -> empiezaPor(b, n, 0xD0, 0xCF, 0x11, 0xE0);
            // Texto: sin bytes nulos y sin la cabecera de un ejecutable de Windows.
            case "txt", "csv" -> !empiezaPor(b, n, 'M', 'Z') && sinBytesNulos(b, n);
            default -> false;
        };
    }

    private static boolean empiezaPor(byte[] b, int n, int... firma) {
        if (n < firma.length) {
            return false;
        }
        for (int i = 0; i < firma.length; i++) {
            if ((b[i] & 0xFF) != firma[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean sinBytesNulos(byte[] b, int n) {
        for (int i = 0; i < n; i++) {
            if (b[i] == 0) {
                return false;
            }
        }
        return true;
    }

    @Transactional(readOnly = true)
    public List<Attach> getAttachments(UUID ticketId) {
        Ticket ticket = getTicketLegible(ticketId);
        User current = userService.getCurrentUser();
        boolean isAgent = esAgenteAsignado(ticket, current);
        boolean isStaff = esPersonalAdministrador(current) || isAgent;

        List<Attach> all = attachRepository.findByTicketAttachIdTicketAndDeletedAttachFalse(ticketId);
        if (isStaff) {
            return all;
        }
        return all.stream()
                .filter(a -> a.getAnswerAttach() == null
                        || !Boolean.TRUE.equals(a.getAnswerAttach().getInternalAnswer()))
                .toList();
    }

    private String sanitize(String name) {
        if (name == null) return "file";
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /**
     * Localiza un adjunto en disco a partir de lo que hay en
     * storage_path_attach.
     *
     * Acepta las tres formas en las que puede encontrarse ese dato porque hay
     * registros escritos antes de que la columna pasara a guardar solo el
     * nombre: la ruta absoluta antigua (carpeta static, ya movida), la ruta
     * relativa con zona, o el nombre unico tal cual.
     *
     * Devuelve null si no hay archivo. Quien llama decide si eso es un 404.
     */
    @Transactional(readOnly = true)
    public Resource abrirAdjunto(Attach adj) {
        String guardado = adj.getStoragePathAttach();
        if (guardado == null || guardado.isBlank()) {
            return null;
        }
        Path base = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path zona = base.resolve(UploadZones.ADJUNTOS);

        Path candidato;
        if (guardado.contains("/") || guardado.contains("\\")) {
            // Ruta absoluta o con separadores: se toma solo el nombre del archivo,
            // que es lo unico que se genera con UUID y no se puede manipular para
            // salirse de la carpeta.
            String nombre = Paths.get(guardado).getFileName().toString();
            candidato = zona.resolve(nombre);
        } else {
            candidato = zona.resolve(guardado);
        }

        // Comprobacion final de contencion: aunque el nombre viniera manipulado,
        // el archivo resuelto tiene que seguir dentro de la zona de adjuntos.
        try {
            if (!candidato.toAbsolutePath().normalize().startsWith(zona)) {
                return null;
            }
        } catch (InvalidPathException e) {
            return null;
        }

if (!Files.isRegularFile(candidato)) {
              // Compatibilidad con los registros anteriores al cambio de zona: el
              // archivo sigue en la ruta absoluta que guardamos en su dia.
              Path antiguo = Paths.get(guardado).toAbsolutePath().normalize();
              // La comprobacion de contencion de arriba solo protegia al candidato.
              // Esta rama se saltaba esa comprobacion por completo, de modo que
              // un storage_path_attach con una ruta absoluta servia cualquier
              // archivo del disco con los permisos del proceso Java: sin
              // necesidad de que la API expusiera la columna, bastaba un registro
              // manipulado por donde fuera. Se cierra exigiendo que el archivo
              // antiguo siga dentro de la raiz de subidas, que es lo unico que
              // puede haber contenido un adjunto historico.
              try {
                  if (!antiguo.startsWith(base)) {
                      return null;
                  }
              } catch (InvalidPathException e) {
                  return null;
              }
              if (Files.isRegularFile(antiguo)) {
                  return new FileSystemResource(antiguo);
              }
              return null;
          }
        return new FileSystemResource(candidato);
    }

    /**
     * Notifica que el usuario actual está escribiendo en el ticket. Se invoca desde
     * el frontend con throttle mientras el campo de respuesta tiene texto.
     */
    @Transactional(readOnly = true)
    public void notifyTyping(UUID ticketId) {
        // No filtra nada por sí mismo, pero sí publica un evento SSE con el
        // nombre del usuario: sin esta comprobación bastaba con llamar al
        // endpoint para enterarte de que alguien está escribiendo en un ticket
        // que no es tuyo.
        Ticket ticket = getTicketLegible(ticketId);
        User current = userService.getCurrentUser();
        if (current == null) {
            return;
        }
        UUID solicitanteId = ticket.getUserTicket() != null ? ticket.getUserTicket().getIdUser() : null;
        UUID agenteId = ticket.getAgentTicket() != null ? ticket.getAgentTicket().getIdUser() : null;

        // La audiencia es la misma que la de las notificaciones: titular, agente
        // asignado y administradores activos. Antes solo se avisaba a las dos
        // primeras, asi que funcionaba en un sentido pero no en el otro: si
        // escribia el titular, un ADMIN que estaba viendo el ticket (y puede
        // hacerlo, los admin ven todos) no recibia el evento y no veia que
        // estaban escribiendo. Al reves si se notaba, porque el titular si
        // estaba en la lista.
        Set<UUID> destinatarios = new HashSet<>();
        if (solicitanteId != null) {
            destinatarios.add(solicitanteId);
        }
        if (agenteId != null) {
            destinatarios.add(agenteId);
        }
        for (String rol : List.of("ADMIN", "SUPER_ADMIN")) {
            for (User admin : userRepository.findByRoleName(rol)) {
                if (admin.getDeletedUser() || !esActivo(admin)) {
                    continue;
                }
                destinatarios.add(admin.getIdUser());
            }
        }
        // Quien escribe no es destinatario de su propio evento.
        destinatarios.remove(current.getIdUser());

        sseEventBroker.publishTyping(ticketId, current.getIdUser(), nombreVisible(current),
                destinatarios);
    }

private String nombreVisible(User u) {
        // Lo que escribe una automatización corre con la sesión del SUPER_ADMIN
        // que la programó, pero no lo escribió esa persona: los avisos decían
        // "superadmin respondió". Se firma como el sistema.
        if (com.mesaayuda.workflow.WorkflowOrigen.actual() != null) {
            return "Automatización (Sistema)";
        }
        // La resolución vive en NombreVisible porque la usa también
        // NotificationService, que es quien compone los correos.
        return NombreVisible.de(u);
    }

private void publishSse(String type, Ticket ticket, UUID actorId, String actorName) {
          sseEventBroker.publish(type, ticket.getIdTicket(), ticket.getNumberTicket(), ticket.getTitleTicket(),
                  ticket.getStatusTicket().getNameStatus(), actorId, actorName,
                  ticket.getAgentTicket() != null ? ticket.getAgentTicket().getIdUser() : null,
                  ticket.getAgentTicket() != null ? ticket.getAgentTicket().getCorporateEmailUser() : null,
                  // El titular se envia para que el canal decida a quien le toca:
                  // el personal lo ve todo y un solicitador solo lo suyo. Es el
                  // equivalente en tiempo real de TicketAccessPolicy.
                  ticket.getUserTicket() != null ? ticket.getUserTicket().getIdUser() : null);
      }

    // ============================ INTERNOS ============================

    private Ticket assignAgent(Ticket ticket, User agent, boolean save) {
        ticket.setAgentTicket(agent);
        if (ticket.getStatusTicket() != null && "NUEVO".equals(ticket.getStatusTicket().getNameStatus())) {
            ticket.setStatusTicket(statusByName("ASIGNADO"));
        }
        return save ? ticketRepository.save(ticket) : ticket;
    }

    /** El número de ticket escrito como "43" o "#43", o null si no es un número. */
    static Long numeroDeTicket(String texto) {
        String limpio = texto == null ? "" : texto.trim();
        if (limpio.startsWith("#")) {
            limpio = limpio.substring(1).trim();
        }
        if (limpio.isEmpty() || limpio.length() > 18 || !limpio.chars().allMatch(Character::isDigit)) {
            return null;
        }
        return Long.parseLong(limpio);
    }

    private Ticket getTicket(UUID id) {
        return ticketRepository.findById(id)
                .filter(t -> !t.getDeletedTicket())
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", "id", id));
    }

// ============================ CONTROL DE ACCESO ============================

/**
 * Miembros del personal. Todos ven la cola completa porque sin eso el
 * kanban de los agentes no funciona: tienen que poder abrir un ticket sin
 * asignar para leerlo y decidir a quién asignárselo.
 *
 * <p>La definición vive en {@link TicketAccessPolicy} y aquí solo se delega,
 * porque RatingService y AuditService consultan el mismo recurso y necesitan
 * exactamente la misma respuesta.
 */
private boolean esPersonal(User u) {
    return accessPolicy.esPersonal(u);
}

private boolean esPersonalAdministrador(User u) {
    return accessPolicy.esPersonalAdministrador(u);
}

/**
 * Si una cuenta está habilitada.
 *
 * <p>Antes se comprobaba con {@code Boolean.TRUE.equals(statusUser)}, pero
 * {@code statusUser} es un String: eso da siempre {@code false} para "ACTIVE",
 * de modo que la condición de descarte se cumplía siempre y a los
 * administradores no les llegaba nunca el aviso, ni de un mensaje nuevo ni
 * de que alguien estaba escribiendo.
 */
private boolean esActivo(User u) {
    return accessPolicy.esActivo(u);
}

private boolean esPropietario(Ticket t, User u) {
    return accessPolicy.esPropietario(t, u);
}

private boolean esAgenteAsignado(Ticket t, User u) {
    return accessPolicy.esAgenteAsignado(t, u);
}

/**
 * Ticket que el usuario actual puede LEER: el suyo propio o cualquiera, si
 * es personal. Antes este método no comprobaba nada, de modo que cualquier
 * cuenta con sesión iniciada, incluido el rol USER, podía leer un ticket
 * ajeno indicando su id en la URL.
 *
 * <p>La regla vive en {@link TicketAccessPolicy} porque RatingService y
 * AuditService necesitan la misma comprobación sobre el mismo recurso.
 */
private Ticket getTicketLegible(UUID id) {
    Ticket ticket = getTicket(id);
    accessPolicy.exigirLectura(ticket, "leer", id);
    return ticket;
}

/**
 * Ticket sobre el que el usuario actual puede ESCRIBIR (responder o subir
 * adjuntos). Es la misma regla que ya aplicaba addAnswer: el titular, el
 * agente asignado o un administrador. Un agente sin asignar el ticket no
 * puede escribir en él, aunque sí pueda abrirlo.
 */
private Ticket getTicketEscribible(UUID id) {
    Ticket ticket = getTicket(id);
    User actual = userService.getCurrentUser();
    if (!accessPolicy.puedeEscribir(ticket, actual)) {
        log.warn("Acceso denegado: {} intentó escribir en el ticket #{}.",
                actual.getCorporateEmailUser(), id);
        throw new AccessDeniedException("No tiene acceso a este ticket");
    }
    return ticket;
}

/**
 * Ticket que el usuario actual puede GESTIONAR: cambiar estado, prioridad o
 * categoría. Lo hace el agente asignado o un administrador, nunca el
 * solicitante.
 *
 * <p>Antes estas operaciones usaban {@link #getTicketEscribible}, que también deja
 * pasar al titular. La pantalla le ocultaba el selector de estado, pero la API lo
 * aceptaba: un USER podía marcar su propio ticket como RESUELTO y con eso dar el
 * SLA por cumplido, o escalarlo y subirle la prioridad saltándose la cola.
 */
private Ticket getTicketGestionable(UUID id, String queSeHacia) {
    Ticket ticket = getTicket(id);
    User actual = userService.getCurrentUser();
    boolean gestiona = accessPolicy.esPersonal(actual) && accessPolicy.puedeEscribir(ticket, actual);
    if (!gestiona) {
        log.warn("Acceso denegado: {} intentó {} del ticket #{}.",
                actual != null ? actual.getCorporateEmailUser() : "desconocido", queSeHacia, id);
        throw new AccessDeniedException("No tiene permisos para gestionar este ticket");
    }
    return ticket;
}

    /**
     * El plazo del SLA depende de prioridad, categoría y tipo. Antes se fijaba
     * solo al crear: si después cambiaba la prioridad (a mano o por NEXA), el
     * ticket seguía con el plazo de la prioridad original. No se toca un ticket
     * ya terminado.
     */
    /** Plazos del SLA del ticket; null si no tiene ninguno aplicado. */
    @Transactional(readOnly = true)
    public com.mesaayuda.dto.TicketSlaResponse sla(UUID ticketId) {
        Ticket ticket = getTicketLegible(ticketId);
        boolean manual = Boolean.TRUE.equals(ticket.getPriorityManualTicket());
        return ticketSlaRepository.findByTicketIdTicket(ticketId)
                .map(ts -> new com.mesaayuda.dto.TicketSlaResponse(
                        ts.getSlaPolicy() == null ? null : ts.getSlaPolicy().getNameSlaPolicy(),
                        ts.getResponseDueAt(), ts.getResolutionDueAt(),
                        ts.getResponseCompletedAt(), ts.getResolutionCompletedAt(),
                        Boolean.TRUE.equals(ts.getResponseBreached()),
                        Boolean.TRUE.equals(ts.getResolutionBreached()),
                        ts.getPausedAt(), manual))
                .orElse(null);
    }

    /** Asigna agente con el enrutamiento de la categoría si el ticket no tiene ninguno. */
    private void enrutarSiSinAgente(Ticket ticket, Category categoria) {
        String estado = ticket.getStatusTicket() == null ? "" : ticket.getStatusTicket().getNameStatus();
        if (ticket.getAgentTicket() != null || "RESUELTO".equals(estado) || "CERRADO".equals(estado)) {
            return;
        }
        User agente = routingService.routeTicket(categoria);
        if (agente == null) {
            return;
        }
        Ticket asignado = assignAgent(ticket, agente, true);
        auditService.record(asignado, "ASSIGN", null, agente.getCorporateEmailUser(),
                "Asignación automática por la categoría " + categoria.getNameCategory());
        notificationService.notifyTicketAssigned(asignado);
        publishSse("TICKET_ASSIGNED", asignado, agente.getIdUser(), agente.getCorporateEmailUser());
    }

    private void recalcularSla(Ticket ticket) {
        String estado = ticket.getStatusTicket() == null ? "" : ticket.getStatusTicket().getNameStatus();
        if ("RESUELTO".equals(estado) || "CERRADO".equals(estado)) {
            return;
        }
        slaService.applySlaToTicket(ticket);
    }

    private Status statusByName(String name) {
        return statusRepository.findByNameStatus(name)
                .filter(s -> !s.getDeletedStatus())
                .orElseThrow(() -> new BadRequestException("Estado inválido: " + name));
    }

    /**
     * Cambia la prioridad del ticket.
     *
     * <p>Se añadió con el motor de workflows, que necesitaba reasignar prioridad
     * al clasificar un ticket. Sigue el mismo camino que {@code changeStatus}:
     * misma comprobación de escritura, misma auditoría y mismo SSE. Un cambio por
     * aquí que no avise a nadie dejaría la pantalla de los agentes mostrando una
     * prioridad que ya no es la real.
     */
    @Transactional
    public TicketResponse changePriority(UUID ticketId, UUID priorityId) {
        Ticket ticket = getTicketGestionable(ticketId, "cambiar la prioridad");
        User current = userService.getCurrentUser();

        Priority nueva = priorityRepository.findById(priorityId)
                .filter(p -> !p.getDeletedPriority())
                .orElseThrow(() -> new BadRequestException("Prioridad inválida: " + priorityId));

        String anterior = ticket.getPriorityTicket() == null
                ? null : ticket.getPriorityTicket().getNamePriority();
        ticket.setPriorityTicket(nueva);
        boolean automatico = com.mesaayuda.workflow.WorkflowOrigen.actual() != null;
        if (!automatico) {
            // La fijó una persona del personal: desde ahora manda sobre la IA.
            ticket.setPriorityManualTicket(true);
        }
        ticket = ticketRepository.save(ticket);
        recalcularSla(ticket);

        auditService.record(ticket, "PRIORITY_CHANGE", anterior, nueva.getNamePriority(),
                automatico ? "Ajustada por una automatización (NEXA)" : null);
        publishSse("TICKET_UPDATED", ticket, current.getIdUser(), current.getCorporateEmailUser());
        log.info("Ticket #{} cambió de prioridad {} -> {}", ticketId, anterior, nueva.getNamePriority());
        // Antes no se publicaba nada: un workflow con "Cambio de prioridad" no se
        // enteraba nunca.
        workflowEventPublisher.ticketCambioPrioridad(ticket, anterior);
        return ticketMapper.toResponse(ticket);
    }

    /**
     * Cambia la categoría del ticket.
     *
     * <p>Mismo motivo y mismo camino que {@link #changePriority}. La categoría es
     * la que manda al equipo, así que cambiarla sin avisar deja el ticket en la
     * bandeja equivocada.
     */
    @Transactional
    public TicketResponse changeCategory(UUID ticketId, UUID categoryId) {
        Ticket ticket = getTicketGestionable(ticketId, "cambiar la categoría");
        User current = userService.getCurrentUser();

        Category nueva = categoryRepository.findById(categoryId)
                .filter(c -> !c.getDeletedCategory())
                .orElseThrow(() -> new BadRequestException("Categoría inválida: " + categoryId));

        String anterior = ticket.getCategoryTicket() == null
                ? null : ticket.getCategoryTicket().getNameCategory();
        ticket.setCategoryTicket(nueva);
        // La categoría decide el equipo. Antes solo se usaba al crear: los
        // tickets de usuarios llegan sin categoría (se toma la primera) y NEXA la
        // corrige después, así que se quedaban en el equipo equivocado y sin
        // agente. Ahora el equipo sigue a la categoría y, si nadie lo atiende
        // aún, se enruta otra vez.
        if (nueva.getTeamCategory() != null) {
            ticket.setTeamTicket(nueva.getTeamCategory());
        }
        ticket = ticketRepository.save(ticket);
        enrutarSiSinAgente(ticket, nueva);

        recalcularSla(ticket);
        auditService.record(ticket, "CATEGORY_CHANGE", anterior, nueva.getNameCategory(), null);
        publishSse("TICKET_UPDATED", ticket, current.getIdUser(), current.getCorporateEmailUser());
log.info("Ticket #{} cambió de categoría {} -> {}", ticketId, anterior, nueva.getNameCategory());
          workflowEventPublisher.ticketCambioCategoria(ticket, anterior);
          return ticketMapper.toResponse(ticket);
    }

    /** Cambia el tipo del ticket (INCIDENTE, REQUERIMIENTO...). Lo usa el nodo TICKET_TYPE. */
    @Transactional
    public TicketResponse changeType(UUID ticketId, UUID typeId) {
        Ticket ticket = getTicketGestionable(ticketId, "cambiar el tipo");
        User current = userService.getCurrentUser();

        TicketType nuevo = ticketTypeRepository.findById(typeId)
                .filter(t -> !t.getDeletedTicketType())
                .orElseThrow(() -> new BadRequestException("Tipo de ticket inválido: " + typeId));

        String anterior = ticket.getTypeTicket() == null ? null : ticket.getTypeTicket().getNameTicketType();
        ticket.setTypeTicket(nuevo);
        ticket = ticketRepository.save(ticket);

        recalcularSla(ticket);
        auditService.record(ticket, "TYPE_CHANGE", anterior, nuevo.getNameTicketType(), null);
        publishSse("TICKET_UPDATED", ticket, current.getIdUser(), current.getCorporateEmailUser());
        log.info("Ticket #{} cambió de tipo {} -> {}", ticketId, anterior, nuevo.getNameTicketType());
        workflowEventPublisher.ticketActualizado(ticket);
        return ticketMapper.toResponse(ticket);
    }
}
