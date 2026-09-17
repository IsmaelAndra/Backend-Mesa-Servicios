package com.mesaayuda.service;

import com.mesaayuda.dto.*;
import com.mesaayuda.entity.*;
import com.mesaayuda.exception.BadRequestException;
import com.mesaayuda.exception.ResourceNotFoundException;
import com.mesaayuda.mapper.AnswerMapper;
import com.mesaayuda.mapper.TicketMapper;
import com.mesaayuda.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.persistence.criteria.Predicate;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class TicketService {

    private static final Logger log = LoggerFactory.getLogger(TicketService.class);

    @Autowired
    private TicketRepository ticketRepository;

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
    private SlaService slaService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private AuditService auditService;

    @Autowired
    private UserService userService;

    @Autowired
    private TicketMapper ticketMapper;

    @Autowired
    private AnswerMapper answerMapper;

    @Value("${app.uploads.dir}")
    private String uploadDir;

    // ============================ CREACIÓN ============================

    public TicketResponse create(TicketRequest request) {
        User requester = userService.getCurrentUser();

        Category category = (request.categoryId() != null)
                ? categoryRepository.findById(request.categoryId())
                        .filter(c -> !c.getDeletedCategory())
                        .orElseThrow(() -> new ResourceNotFoundException("Categoría", "id", request.categoryId()))
                : categoryRepository.findFirstByDeletedCategoryFalseOrderByIdCategoryAsc()
                        .orElseThrow(() -> new BadRequestException("No hay categorías disponibles"));

        TicketType type = (request.typeId() != null)
                ? ticketTypeRepository.findById(request.typeId())
                        .filter(t -> !t.getDeletedTicketType())
                        .orElseThrow(() -> new ResourceNotFoundException("Tipo de ticket", "id", request.typeId()))
                : ticketTypeRepository.findTopByDeletedTicketTypeFalseOrderByIdTicketTypeAsc().orElse(null);

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
        ticket.setCategoryTicket(category);
        ticket.setTypeTicket(type);
        ticket.setStatusTicket(newStatus);
        ticket.setPriorityTicket(priority);
        ticket.setTeamTicket(category.getTeamCategory());
        ticket.setDeletedTicket(false);
        ticket = ticketRepository.save(ticket);

        User routedAgent = routingService.routeTicket(category);
        if (routedAgent != null) {
            ticket = assignAgent(ticket, routedAgent, false);
        }

        slaService.applySlaToTicket(ticket);
        auditService.record(ticket, "CREATE", null, ticket.getTitleTicket(),
                "Ticket creado por " + requester.getCorporateEmailUser());
        notificationService.notifyTicketCreated(ticket);
        if (ticket.getAgentTicket() != null) {
            notificationService.notifyTicketAssigned(ticket);
        }

        log.info("Ticket #{} creado por {}", ticket.getIdTicket(), requester.getCorporateEmailUser());
        return ticketMapper.toResponse(ticket);
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
                                       Long areaId, Long agentId, Long requesterId,
                                       String keyword, int page, int size) {
        Specification<Ticket> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("deletedTicket")));
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
            if (agentId != null) {
                predicates.add(cb.equal(root.get("agentTicket").get("idUser"), agentId));
            }
            if (requesterId != null) {
                predicates.add(cb.equal(root.get("userTicket").get("idUser"), requesterId));
            }
            if (keyword != null && !keyword.isBlank()) {
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("titleTicket")), "%" + keyword.toLowerCase() + "%"),
                        cb.like(cb.lower(root.get("descriptionTicket")), "%" + keyword.toLowerCase() + "%")
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAtTicket"));
        return ticketRepository.findAll(spec, pageable).map(ticketMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public TicketResponse findById(Long id) {
        return ticketMapper.toResponse(getTicket(id));
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> myTickets() {
        User current = userService.getCurrentUser();
        return ticketRepository
                .findByUserTicketIdUserAndDeletedTicketFalseOrderByCreatedAtTicketDesc(current.getIdUser())
                .stream().map(ticketMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> assignedTickets() {
        User current = userService.getCurrentUser();
        return ticketRepository
                .findByAgentTicketIdUserAndDeletedTicketFalseOrderByCreatedAtTicketDesc(current.getIdUser())
                .stream().map(ticketMapper::toResponse).toList();
    }

    // ============================ OPERACIONES ============================

    public TicketResponse assign(Long ticketId, AssignRequest request) {
        Ticket ticket = getTicket(ticketId);
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
        return ticketMapper.toResponse(ticket);
    }

    public TicketResponse changeStatus(Long ticketId, StatusChangeRequest request) {
        User current = userService.getCurrentUser();
        String role = current.getRolUser().getNameRole();
        boolean canChange = "AGENT".equals(role) || "ADMIN".equals(role) || "SUPER_ADMIN".equals(role);
        if (!canChange) {
            throw new BadRequestException("Un usuario no puede modificar el estado de los tickets");
        }

        Ticket ticket = getTicket(ticketId);
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
        log.info("Ticket #{} cambió de estado {} -> {}", ticketId, oldStatus, newStatus.getNameStatus());
        return ticketMapper.toResponse(ticket);
    }

    // ============================ RESPUESTAS ============================

    public AnswerResponse addAnswer(Long ticketId, AnswerRequest request) {
        Ticket ticket = getTicket(ticketId);
        User current = userService.getCurrentUser();

        String role = current.getRolUser().getNameRole();
        boolean isOwner = ticket.getUserTicket().getIdUser().equals(current.getIdUser());
        boolean isAgent = ticket.getAgentTicket() != null
                && ticket.getAgentTicket().getIdUser().equals(current.getIdUser());
        boolean isStaff = "ADMIN".equals(role) || "SUPER_ADMIN".equals(role) || isAgent;
        if (!isOwner && !isStaff) {
            throw new BadRequestException("No tiene permisos para responder este ticket");
        }

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

        User notified = current.getIdUser().equals(ticket.getUserTicket().getIdUser())
                ? ticket.getAgentTicket() : ticket.getUserTicket();
        if (notified != null) {
            notificationService.sendNotification(ticket, notified, "TICKET_ACTUALIZADO",
                    "Ticket #%d actualizado".formatted(ticketId),
                    "Nueva respuesta: " + request.message());
        }
        return answerMapper.toResponse(answer);
    }

    @Transactional(readOnly = true)
    public List<AnswerResponse> getAnswers(Long ticketId) {
        Ticket ticket = getTicket(ticketId);
        User current = userService.getCurrentUser();

        String role = current.getRolUser().getNameRole();
        boolean isAgent = ticket.getAgentTicket() != null
                && ticket.getAgentTicket().getIdUser().equals(current.getIdUser());
        boolean isStaff = "ADMIN".equals(role) || "SUPER_ADMIN".equals(role) || isAgent;

        return answerRepository
                .findByTicketAnswerIdTicketAndDeletedAnswerFalseOrderByCreatedAtAnswerAsc(ticketId)
                .stream()
                .filter(a -> isStaff || !Boolean.TRUE.equals(a.getInternalAnswer()))
                .map(answerMapper::toResponse)
                .toList();
    }

    // ============================ ADJUNTOS ============================

    public Attach uploadAttachment(Long ticketId, Long answerId, MultipartFile file) {
        Ticket ticket = getTicket(ticketId);
        if (file.isEmpty()) {
            throw new BadRequestException("El archivo está vacío");
        }

        String originalName = file.getOriginalFilename();
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
            Path dir = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            String uniqueName = UUID.randomUUID() + "_" + sanitize(originalName);
            Path target = dir.resolve(uniqueName);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            Attach attach = new Attach();
            attach.setFileNameAttach(originalName);
            attach.setStoragePathAttach(target.toString());
            attach.setSizeFileAttach(file.getSize());
            attach.setMimeTypeAttach(file.getContentType());
            attach.setTicketAttach(ticket);
            attach.setAnswerAttach(answer);
            attach.setDeletedAttach(false);
            attach = attachRepository.save(attach);
            auditService.record(ticket, "ATTACH",
                    answer == null ? null : "Respuesta " + answer.getIdAnswer(),
                    originalName, "Archivo adjunto");
            return attach;
        } catch (IOException e) {
            throw new BadRequestException("No se pudo guardar el archivo: " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<Attach> getAttachments(Long ticketId) {
        Ticket ticket = getTicket(ticketId);
        User current = userService.getCurrentUser();
        String role = current.getRolUser().getNameRole();
        boolean isAgent = ticket.getAgentTicket() != null
                && ticket.getAgentTicket().getIdUser().equals(current.getIdUser());
        boolean isStaff = "ADMIN".equals(role) || "SUPER_ADMIN".equals(role) || isAgent;

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

    // ============================ INTERNOS ============================

    private Ticket assignAgent(Ticket ticket, User agent, boolean save) {
        ticket.setAgentTicket(agent);
        if (ticket.getStatusTicket() != null && "NUEVO".equals(ticket.getStatusTicket().getNameStatus())) {
            ticket.setStatusTicket(statusByName("ASIGNADO"));
        }
        return save ? ticketRepository.save(ticket) : ticket;
    }

    private Ticket getTicket(Long id) {
        return ticketRepository.findById(id)
                .filter(t -> !t.getDeletedTicket())
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", "id", id));
    }

    private Status statusByName(String name) {
        return statusRepository.findByNameStatus(name)
                .filter(s -> !s.getDeletedStatus())
                .orElseThrow(() -> new BadRequestException("Estado inválido: " + name));
    }
}