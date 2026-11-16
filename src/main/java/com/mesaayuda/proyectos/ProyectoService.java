package com.mesaayuda.proyectos;

import com.mesaayuda.entity.Area;
import com.mesaayuda.entity.User;
import com.mesaayuda.exception.BadRequestException;
import com.mesaayuda.exception.ResourceNotFoundException;
import com.mesaayuda.repository.AreaRepository;
import com.mesaayuda.repository.UserRepository;
import com.mesaayuda.security.Roles;
import com.mesaayuda.service.JefaturaService;
import com.mesaayuda.service.NotificationService;
import com.mesaayuda.service.SystemAuditService;
import com.mesaayuda.service.UserService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Proyectos (Fase 3). Reglas:
 * <ul>
 *   <li>Un proyecto es de UN área: la del jefe que lo crea (el súper admin elige).</li>
 *   <li><b>Gestor</b>: súper admin o jefe del área dueña. Edita el proyecto, sus
 *       sprints, objetivos, áreas invitadas y miembros.</li>
 *   <li>Otra área participa solo si su jefe ACEPTA la invitación (aviso en la
 *       campana en tiempo real). Ese <b>jefe participante</b> añade o quita a
 *       la gente de su área y trabaja en el tablero.</li>
 *   <li>Los <b>miembros</b> trabajan en el tablero (tarjetas). El auditor solo lee.</li>
 *   <li>El avance es la suma de los pesos de las tarjetas HECHO sobre el total.</li>
 * </ul>
 * Los equipos de proyecto (miembros) son distintos de los equipos de atención.
 */
@Service
@Transactional
public class ProyectoService {

    public static final List<String> ESTADOS_PROYECTO = List.of("PLANIFICADO", "EN_CURSO", "PAUSADO", "TERMINADO", "CANCELADO");
    public static final List<String> ESTADOS_ITEM = List.of("POR_HACER", "EN_CURSO", "EN_REVISION", "HECHO");
    public static final List<String> ESTADOS_SPRINT = List.of("PLANIFICADO", "ACTIVO", "CERRADO");
    private static final UUID NINGUNA = new UUID(0, 0);

    private final ProyectoRepository proyectos;
    private final ProyectoAreaRepository areasProyecto;
    private final ProyectoMiembroRepository miembros;
    private final ProyectoSprintRepository sprints;
    private final ProyectoItemRepository items;
    private final ProyectoObjetivoRepository objetivos;
    private final UserService userService;
    private final UserRepository userRepository;
    private final AreaRepository areaRepository;
    private final JefaturaService jefaturaService;
    private final NotificationService notificationService;
    private final SystemAuditService auditoria;

    public ProyectoService(ProyectoRepository proyectos, ProyectoAreaRepository areasProyecto,
                           ProyectoMiembroRepository miembros, ProyectoSprintRepository sprints,
                           ProyectoItemRepository items, ProyectoObjetivoRepository objetivos,
                           UserService userService, UserRepository userRepository, AreaRepository areaRepository,
                           JefaturaService jefaturaService, NotificationService notificationService,
                           SystemAuditService auditoria) {
        this.proyectos = proyectos;
        this.areasProyecto = areasProyecto;
        this.miembros = miembros;
        this.sprints = sprints;
        this.items = items;
        this.objetivos = objetivos;
        this.userService = userService;
        this.userRepository = userRepository;
        this.areaRepository = areaRepository;
        this.jefaturaService = jefaturaService;
        this.notificationService = notificationService;
        this.auditoria = auditoria;
    }

    // ================================================================== DTO

    public record ProyectoRequest(String nombre, String descripcion, UUID areaId, String estado, String icono,
                                  String color, LocalDate inicio, LocalDate fin) {
    }

    public record SprintRequest(String nombre, String objetivo, LocalDate inicio, LocalDate fin, String estado) {
    }

    public record ItemRequest(String titulo, String descripcion, String estado, Integer peso, UUID responsableId,
                              LocalDate limite, UUID sprintId) {
    }

    public record MoverRequest(String estado, Integer orden) {
    }

    public record ObjetivoRequest(String descripcion) {
    }

    public record LogroRequest(boolean logrado, String nota) {
    }

    public record MiembroRequest(UUID userId, String rol) {
    }

    public record DecisionRequest(boolean aceptar, String motivo) {
    }

    public record Resumen(UUID id, String nombre, String descripcion, UUID areaId, String area, String estado,
                          String icono, String color, LocalDate inicio, LocalDate fin, int avance, int tarjetas,
                          int hechas, int miembros, List<String> areasParticipantes, boolean gestionar,
                          OffsetDateTime actualizado) {
    }

    public record AreaVista(UUID areaId, String nombre, String estado, String motivo, OffsetDateTime decididoAt) {
    }

    public record MiembroVista(UUID userId, String nombre, String correo, UUID areaId, String area, String rol) {
    }

    public record SprintVista(UUID id, String nombre, String objetivo, LocalDate inicio, LocalDate fin, String estado,
                              int avance, int tarjetas) {
    }

    public record ItemVista(UUID id, String titulo, String descripcion, String estado, int peso, UUID responsableId,
                            String responsable, LocalDate limite, int orden, UUID sprintId) {
    }

    public record ObjetivoVista(UUID id, String descripcion, OffsetDateTime logradoAt, String notaLogro) {
    }

    /** Lo que la persona puede hacer en el proyecto (el frontend esconde lo demás). */
    public record Permisos(boolean gestionar, boolean editarTablero, boolean agregarMiembros) {
    }

    public record Detalle(Resumen proyecto, List<AreaVista> areas, List<MiembroVista> miembros,
                          List<SprintVista> sprints, List<ItemVista> items, List<ObjetivoVista> objetivos,
                          Permisos permisos) {
    }

    public record Invitacion(UUID proyectoId, String proyecto, String descripcion, String areaDuena,
                             String invitadoPor, OffsetDateTime fecha) {
    }

    // ================================================================== contexto y permisos

    /** Quién actúa y qué papel tiene en un proyecto. */
    private record Ctx(User u, String rol, UUID areaJefe, Proyecto p, boolean gestor, boolean jefeParticipante,
                       boolean miembro) {
        boolean puedeLeer() {
            return gestor || jefeParticipante || miembro || Roles.veTodo(rol);
        }

        boolean puedeTablero() {
            return (gestor || jefeParticipante || miembro) && !Roles.AUDITOR.equals(rol);
        }
    }

    private User actual() {
        return userService.getCurrentUser();
    }

    private static String rol(User u) {
        return u == null || u.getRolUser() == null ? null : u.getRolUser().getNameRole();
    }

    /** Área del jefe, o null si no es jefe. */
    private UUID areaJefe(User u) {
        Set<UUID> a = jefaturaService.areasACargo(u);
        return a.isEmpty() ? null : a.iterator().next();
    }

    private Ctx ctx(UUID proyectoId) {
        Proyecto p = proyectos.findById(proyectoId).filter(x -> !Boolean.TRUE.equals(x.getDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Proyecto", "id", proyectoId));
        User u = actual();
        String rol = rol(u);
        UUID area = areaJefe(u);
        boolean gestor = Roles.esSuperAdmin(rol) || (area != null && area.equals(p.getAreaId()));
        boolean participante = area != null && !gestor && areasProyecto.findByProyectoIdAndAreaId(p.getIdProyecto(), area)
                .map(pa -> "ACEPTADA".equals(pa.getEstado())).orElse(false);
        boolean miembro = miembros.existsByProyectoIdAndUserId(p.getIdProyecto(), u.getIdUser());
        return new Ctx(u, rol, area, p, gestor, participante, miembro);
    }

    // ================================================================== para el chat

    /** Quién habla en el chat de un proyecto: lee quien ve el proyecto; escribe el equipo (no el auditor). */
    public record AccesoChat(User u, Proyecto p, boolean escribe) {
    }

    /** Quien tiene la sesión (lo usa el chat para los "no leídos"). */
    public User accesoActual() {
        return actual();
    }

    @Transactional(readOnly = true)
    public AccesoChat accesoChat(UUID id) {
        Ctx c = lector(id);
        return new AccesoChat(c.u(), c.p(), c.puedeTablero());
    }

    /**
     * Quienes reciben el chat en vivo y cuentan para "no leídos": los miembros,
     * los jefes del área dueña y los de las áreas que aceptaron.
     */
    @Transactional(readOnly = true)
    public Set<UUID> participantes(UUID id) {
        Proyecto p = proyectos.findById(id).orElseThrow(() -> new ResourceNotFoundException("Proyecto", "id", id));
        Set<UUID> ids = new java.util.HashSet<>();
        miembros.findByProyectoIdOrderByCreatedAtAsc(id).forEach(m -> ids.add(m.getUserId()));
        jefaturaService.jefesDe(p.getAreaId()).forEach(j -> ids.add(j.getIdUser()));
        areasProyecto.findByProyectoIdOrderByCreatedAtAsc(id).stream()
                .filter(pa -> "ACEPTADA".equals(pa.getEstado()))
                .forEach(pa -> jefaturaService.jefesDe(pa.getAreaId()).forEach(j -> ids.add(j.getIdUser())));
        return ids;
    }

    private Ctx lector(UUID id) {
        Ctx c = ctx(id);
        if (!c.puedeLeer()) {
            throw new AccessDeniedException("No participa en este proyecto");
        }
        return c;
    }

    private Ctx gestor(UUID id) {
        Ctx c = ctx(id);
        if (!c.gestor()) {
            throw new AccessDeniedException("Solo el jefe del área del proyecto o el súper administrador pueden hacerlo");
        }
        return c;
    }

    private Ctx tablero(UUID id) {
        Ctx c = ctx(id);
        if (!c.puedeTablero()) {
            throw new AccessDeniedException("Solo el equipo del proyecto trabaja en el tablero");
        }
        return c;
    }

    // ================================================================== proyectos

    @Transactional(readOnly = true)
    public List<Resumen> listar() {
        User u = actual();
        String rol = rol(u);
        UUID area = areaJefe(u);
        List<Proyecto> lista = Roles.veTodo(rol)
                ? proyectos.findByDeletedFalseOrderByUpdatedAtDesc()
                : proyectos.visiblesPara(u.getIdUser(), area == null ? NINGUNA : area);
        return resumenes(lista, u, area);
    }

    public Resumen crear(ProyectoRequest r) {
        User u = actual();
        String rol = rol(u);
        UUID area;
        if (Roles.esSuperAdmin(rol)) {
            if (r == null || r.areaId() == null) {
                throw new BadRequestException("Elija el área del proyecto");
            }
            area = r.areaId();
        } else if (Roles.esJefe(rol) && areaJefe(u) != null) {
            // El jefe crea proyectos solo de su área, venga lo que venga en el cuerpo.
            area = areaJefe(u);
        } else {
            throw new AccessDeniedException("Crean proyectos los jefes de área y el súper administrador");
        }
        areaValida(area);
        Proyecto p = new Proyecto();
        p.setAreaId(area);
        p.setCreadoPor(u.getIdUser());
        p.setEstado("PLANIFICADO");
        aplicar(p, r);
        p = proyectos.save(p);
        // Quien lo crea queda como responsable del equipo.
        ProyectoMiembro m = new ProyectoMiembro();
        m.setProyectoId(p.getIdProyecto());
        m.setUserId(u.getIdUser());
        m.setRol("RESPONSABLE");
        miembros.save(m);
        auditoria.record("PROYECTO_CREADO", "Proyecto", p.getIdProyecto(), u.getCorporateEmailUser(), p.getNombre());
        return resumenes(List.of(p), u, areaJefe(u)).get(0);
    }

    public Resumen editar(UUID id, ProyectoRequest r) {
        Ctx c = gestor(id);
        aplicar(c.p(), r);
        // Acortar el proyecto no puede dejar sprints ni tarjetas fuera de su plazo.
        for (ProyectoSprint s : sprints.findByProyectoIdAndDeletedFalseOrderByInicioAscCreatedAtAsc(id)) {
            dentroDe(c.p().getInicio(), c.p().getFin(), s.getInicio(), "El sprint \"" + s.getNombre() + "\"", "del proyecto");
            dentroDe(c.p().getInicio(), c.p().getFin(), s.getFin(), "El sprint \"" + s.getNombre() + "\"", "del proyecto");
        }
        for (ProyectoItem i : items.findByProyectoIdAndDeletedFalseOrderByOrdenAscCreatedAtAsc(id)) {
            dentroDe(c.p().getInicio(), c.p().getFin(), i.getLimite(), "La tarjeta \"" + i.getTitulo() + "\"", "del proyecto");
        }
        if (r != null && r.estado() != null) {
            if (!ESTADOS_PROYECTO.contains(r.estado())) {
                throw new BadRequestException("Estado de proyecto no válido");
            }
            if ("TERMINADO".equals(r.estado()) && !"TERMINADO".equals(c.p().getEstado())) {
                exigirTerminado(id);
            }
            c.p().setEstado(r.estado());
        }
        Proyecto p = proyectos.save(c.p());
        return resumenes(List.of(p), c.u(), c.areaJefe()).get(0);
    }

    public void eliminar(UUID id) {
        Ctx c = gestor(id);
        c.p().setDeleted(true);
        proyectos.save(c.p());
        auditoria.record("PROYECTO_ELIMINADO", "Proyecto", id, c.u().getCorporateEmailUser(), c.p().getNombre());
    }

    @Transactional(readOnly = true)
    public Detalle detalle(UUID id) {
        Ctx c = lector(id);
        Proyecto p = c.p();
        List<ProyectoArea> pas = areasProyecto.findByProyectoIdOrderByCreatedAtAsc(id);
        List<ProyectoMiembro> ms = miembros.findByProyectoIdOrderByCreatedAtAsc(id);
        List<ProyectoItem> its = items.findByProyectoIdAndDeletedFalseOrderByOrdenAscCreatedAtAsc(id);
        List<ProyectoSprint> sps = sprints.findByProyectoIdAndDeletedFalseOrderByInicioAscCreatedAtAsc(id);
        List<ProyectoObjetivo> obs = objetivos.findByProyectoIdAndDeletedFalseOrderByOrdenAscCreatedAtAsc(id);

        Set<UUID> ids = new HashSet<>();
        ms.forEach(m -> ids.add(m.getUserId()));
        its.forEach(i -> { if (i.getResponsableId() != null) ids.add(i.getResponsableId()); });
        Map<UUID, User> personas = porId(userRepository.findAllById(ids), User::getIdUser);
        Map<UUID, String> areas = nombresDeAreas();

        List<AreaVista> areasVista = pas.stream()
                .map(pa -> new AreaVista(pa.getAreaId(), areas.getOrDefault(pa.getAreaId(), "—"), pa.getEstado(),
                        pa.getMotivo(), pa.getDecididoAt()))
                .toList();
        List<MiembroVista> miembrosVista = ms.stream()
                .map(m -> {
                    User x = personas.get(m.getUserId());
                    return new MiembroVista(m.getUserId(), nombre(x), x == null ? null : x.getCorporateEmailUser(),
                            areaDe(x), areaDe(x) == null ? null : areas.get(areaDe(x)), m.getRol());
                })
                .sorted(Comparator.comparing((MiembroVista m) -> !"RESPONSABLE".equals(m.rol()))
                        .thenComparing(m -> m.nombre().toLowerCase()))
                .toList();
        List<ItemVista> itemsVista = its.stream()
                .map(i -> new ItemVista(i.getIdProyectoItem(), i.getTitulo(), i.getDescripcion(), i.getEstado(),
                        i.getPeso(), i.getResponsableId(),
                        i.getResponsableId() == null ? null : nombre(personas.get(i.getResponsableId())),
                        i.getLimite(), i.getOrden(), i.getSprintId()))
                .toList();
        List<SprintVista> sprintsVista = sps.stream()
                .map(s -> {
                    List<ProyectoItem> delSprint = its.stream()
                            .filter(i -> s.getIdProyectoSprint().equals(i.getSprintId())).toList();
                    return new SprintVista(s.getIdProyectoSprint(), s.getNombre(), s.getObjetivo(), s.getInicio(),
                            s.getFin(), s.getEstado(), avance(delSprint), delSprint.size());
                })
                .toList();
        List<ObjetivoVista> objetivosVista = obs.stream()
                .map(o -> new ObjetivoVista(o.getIdProyectoObjetivo(), o.getDescripcion(), o.getLogradoAt(),
                        o.getNotaLogro()))
                .toList();
        Resumen resumen = resumen(p, its, ms.size(), pas, areas, c.gestor());
        return new Detalle(resumen, areasVista, miembrosVista, sprintsVista, itemsVista, objetivosVista,
                new Permisos(c.gestor(), c.puedeTablero(), c.gestor() || c.jefeParticipante()));
    }

    // ================================================================== áreas invitadas

    /** El gestor invita a otra área; sus jefes reciben el aviso en la campana al instante. */
    public void invitarArea(UUID id, UUID areaId) {
        Ctx c = gestor(id);
        Area area = areaValida(areaId);
        if (areaId.equals(c.p().getAreaId())) {
            throw new BadRequestException("Esa es el área dueña del proyecto");
        }
        ProyectoArea pa = areasProyecto.findByProyectoIdAndAreaId(id, areaId).orElseGet(ProyectoArea::new);
        if ("ACEPTADA".equals(pa.getEstado()) || "PENDIENTE".equals(pa.getEstado())) {
            throw new BadRequestException("El área " + area.getNameArea() + " ya está invitada");
        }
        pa.setProyectoId(id);
        pa.setAreaId(areaId);
        pa.setEstado("PENDIENTE");
        pa.setInvitadoPor(c.u().getIdUser());
        pa.setDecididoPor(null);
        pa.setDecididoAt(null);
        pa.setMotivo(null);
        areasProyecto.save(pa);
        Map<UUID, String> areas = nombresDeAreas();
        for (User jefe : jefaturaService.jefesDe(areaId)) {
            notificationService.notifyProyecto(jefe, "PROYECTO_INVITACION",
                    "Invitación al proyecto " + c.p().getNombre(),
                    "<p><b>" + esc(nombre(c.u())) + "</b> (" + esc(areas.get(c.p().getAreaId())) + ") invita al área <b>"
                            + esc(area.getNameArea()) + "</b> a participar en el proyecto <b>" + esc(c.p().getNombre())
                            + "</b>.</p><p>Acepte o rechace la invitación en Proyectos.</p>",
                    "proyectos");
        }
        auditoria.record("PROYECTO_INVITACION", "Proyecto", id, c.u().getCorporateEmailUser(), area.getNameArea());
    }

    /** El jefe del área invitada acepta o rechaza; se avisa a quien invitó. */
    public void decidir(UUID id, UUID areaId, DecisionRequest d) {
        User u = actual();
        UUID area = areaJefe(u);
        if (area == null || !area.equals(areaId)) {
            throw new AccessDeniedException("Solo el jefe del área invitada puede aceptar o rechazar");
        }
        ProyectoArea pa = areasProyecto.findByProyectoIdAndAreaId(id, areaId)
                .filter(x -> "PENDIENTE".equals(x.getEstado()))
                .orElseThrow(() -> new BadRequestException("No hay una invitación pendiente para su área"));
        Proyecto p = proyectos.findById(id).filter(x -> !Boolean.TRUE.equals(x.getDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Proyecto", "id", id));
        boolean acepta = d != null && d.aceptar();
        pa.setEstado(acepta ? "ACEPTADA" : "RECHAZADA");
        pa.setDecididoPor(u.getIdUser());
        pa.setDecididoAt(OffsetDateTime.now());
        pa.setMotivo(d == null || d.motivo() == null || d.motivo().isBlank() ? null : recortar(d.motivo().trim(), 500));
        areasProyecto.save(pa);
        String nombreArea = nombresDeAreas().get(areaId);
        if (pa.getInvitadoPor() != null) {
            userRepository.findById(pa.getInvitadoPor()).ifPresent(inv -> notificationService.notifyProyecto(inv,
                    "PROYECTO_RESPUESTA",
                    (acepta ? "Aceptada" : "Rechazada") + " la invitación a " + p.getNombre(),
                    "<p>El área <b>" + esc(nombreArea) + "</b> " + (acepta ? "aceptó" : "rechazó")
                            + " participar en el proyecto <b>" + esc(p.getNombre()) + "</b>.</p>"
                            + (pa.getMotivo() == null ? "" : "<p>Motivo: " + esc(pa.getMotivo()) + "</p>"),
                    "proyectos/" + id));
        }
        auditoria.record(acepta ? "PROYECTO_ACEPTADO" : "PROYECTO_RECHAZADO", "Proyecto", id,
                u.getCorporateEmailUser(), nombreArea);
    }

    /** El gestor saca a un área; su gente sale también del equipo. */
    public void quitarArea(UUID id, UUID areaId) {
        Ctx c = gestor(id);
        areasProyecto.findByProyectoIdAndAreaId(id, areaId).ifPresent(areasProyecto::delete);
        List<ProyectoMiembro> ms = miembros.findByProyectoIdOrderByCreatedAtAsc(id);
        Map<UUID, User> personas = porId(userRepository.findAllById(ms.stream().map(ProyectoMiembro::getUserId).toList()),
                User::getIdUser);
        ms.stream().filter(m -> areaId.equals(areaDe(personas.get(m.getUserId())))).forEach(miembros::delete);
        auditoria.record("PROYECTO_AREA_QUITADA", "Proyecto", id, c.u().getCorporateEmailUser(), String.valueOf(areaId));
    }

    /** Invitaciones pendientes para el área del jefe que pregunta. */
    @Transactional(readOnly = true)
    public List<Invitacion> invitaciones() {
        UUID area = areaJefe(actual());
        if (area == null) {
            return List.of();
        }
        Map<UUID, String> areas = nombresDeAreas();
        List<Invitacion> out = new ArrayList<>();
        for (ProyectoArea pa : areasProyecto.findByAreaIdAndEstadoOrderByCreatedAtDesc(area, "PENDIENTE")) {
            proyectos.findById(pa.getProyectoId()).filter(p -> !Boolean.TRUE.equals(p.getDeleted())).ifPresent(p ->
                    out.add(new Invitacion(p.getIdProyecto(), p.getNombre(), p.getDescripcion(),
                            areas.get(p.getAreaId()),
                            pa.getInvitadoPor() == null ? null : nombre(userRepository.findById(pa.getInvitadoPor()).orElse(null)),
                            pa.getCreatedAt())));
        }
        return out;
    }

    // ================================================================== equipo

    /** Personas que se pueden añadir: del área dueña y de las áreas que aceptaron (el jefe participante, solo de la suya). */
    @Transactional(readOnly = true)
    public List<MiembroVista> candidatos(UUID id) {
        Ctx c = ctx(id);
        Set<UUID> permitidas = areasParaMiembros(c);
        Set<UUID> ya = miembros.findByProyectoIdOrderByCreatedAtAsc(id).stream().map(ProyectoMiembro::getUserId)
                .collect(Collectors.toSet());
        Map<UUID, String> areas = nombresDeAreas();
        return userRepository.findByStatusUserAndDeletedUserFalse("ACTIVE").stream()
                .filter(x -> !ya.contains(x.getIdUser()))
                .filter(x -> !Roles.AUDITOR.equals(rol(x)))
                .filter(x -> areaDe(x) != null && permitidas.contains(areaDe(x)))
                .map(x -> new MiembroVista(x.getIdUser(), nombre(x), x.getCorporateEmailUser(), areaDe(x),
                        areas.get(areaDe(x)), null))
                .sorted(Comparator.comparing(m -> m.nombre().toLowerCase()))
                .toList();
    }

    public void agregarMiembro(UUID id, MiembroRequest r) {
        Ctx c = ctx(id);
        Set<UUID> permitidas = areasParaMiembros(c);
        if (r == null || r.userId() == null) {
            throw new BadRequestException("Elija a la persona");
        }
        User x = userRepository.findById(r.userId()).filter(y -> !Boolean.TRUE.equals(y.getDeletedUser()))
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", "id", r.userId()));
        if (areaDe(x) == null || !permitidas.contains(areaDe(x))) {
            throw new AccessDeniedException("Esa persona es de un área que no participa en el proyecto o que no está a su cargo");
        }
        if (miembros.existsByProyectoIdAndUserId(id, x.getIdUser())) {
            return;
        }
        ProyectoMiembro m = new ProyectoMiembro();
        m.setProyectoId(id);
        m.setUserId(x.getIdUser());
        // Solo el gestor nombra responsables.
        m.setRol(c.gestor() && "RESPONSABLE".equals(r.rol()) ? "RESPONSABLE" : "MIEMBRO");
        miembros.save(m);
        if (!x.getIdUser().equals(c.u().getIdUser())) {
            notificationService.notifyProyecto(x, "PROYECTO_MIEMBRO", "Ahora es parte del proyecto " + c.p().getNombre(),
                    "<p><b>" + esc(nombre(c.u())) + "</b> le añadió al equipo del proyecto <b>"
                            + esc(c.p().getNombre()) + "</b>.</p>", "proyectos/" + id);
        }
    }

    public void quitarMiembro(UUID id, UUID userId) {
        Ctx c = ctx(id);
        Set<UUID> permitidas = areasParaMiembros(c);
        ProyectoMiembro m = miembros.findByProyectoIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Miembro", "id", userId));
        User x = userRepository.findById(userId).orElse(null);
        if (!c.gestor() && (x == null || !permitidas.contains(areaDe(x)))) {
            throw new AccessDeniedException("Solo puede quitar a personas de su área");
        }
        miembros.delete(m);
    }

    /** Áreas cuya gente puede añadir quien actúa: el gestor, la dueña y las aceptadas; el jefe participante, la suya. */
    private Set<UUID> areasParaMiembros(Ctx c) {
        if (c.gestor()) {
            Set<UUID> s = new LinkedHashSet<>();
            s.add(c.p().getAreaId());
            areasProyecto.findByProyectoIdOrderByCreatedAtAsc(c.p().getIdProyecto()).stream()
                    .filter(pa -> "ACEPTADA".equals(pa.getEstado())).forEach(pa -> s.add(pa.getAreaId()));
            return s;
        }
        if (c.jefeParticipante()) {
            return Set.of(c.areaJefe());
        }
        throw new AccessDeniedException("Solo los jefes de las áreas del proyecto arman el equipo");
    }

    // ================================================================== sprints

    public SprintVista crearSprint(UUID id, SprintRequest r) {
        Ctx c = gestor(id);
        ProyectoSprint s = new ProyectoSprint();
        s.setProyectoId(id);
        s.setEstado("PLANIFICADO");
        aplicar(c.p(), s, r);
        s = sprints.save(s);
        return new SprintVista(s.getIdProyectoSprint(), s.getNombre(), s.getObjetivo(), s.getInicio(), s.getFin(),
                s.getEstado(), 0, 0);
    }

    public void editarSprint(UUID id, UUID sprintId, SprintRequest r) {
        Ctx c = gestor(id);
        ProyectoSprint s = sprintDe(id, sprintId);
        aplicar(c.p(), s, r);
        // Las tarjetas del sprint deben seguir dentro de su plazo.
        for (ProyectoItem i : items.findBySprintIdAndDeletedFalse(sprintId)) {
            dentroDe(s.getInicio(), s.getFin(), i.getLimite(), "La tarjeta \"" + i.getTitulo() + "\"", "del sprint");
        }
        if (r != null && r.estado() != null) {
            if (!ESTADOS_SPRINT.contains(r.estado())) {
                throw new BadRequestException("Estado de sprint no válido");
            }
            s.setEstado(r.estado());
        }
        sprints.save(s);
    }

    /** Las tarjetas del sprint borrado vuelven al backlog (sin sprint). */
    public void eliminarSprint(UUID id, UUID sprintId) {
        gestor(id);
        ProyectoSprint s = sprintDe(id, sprintId);
        s.setDeleted(true);
        sprints.save(s);
        for (ProyectoItem i : items.findBySprintIdAndDeletedFalse(sprintId)) {
            i.setSprintId(null);
            items.save(i);
        }
    }

    private ProyectoSprint sprintDe(UUID id, UUID sprintId) {
        return sprints.findById(sprintId)
                .filter(s -> id.equals(s.getProyectoId()) && !Boolean.TRUE.equals(s.getDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Sprint", "id", sprintId));
    }

    private static void aplicar(Proyecto p, ProyectoSprint s, SprintRequest r) {
        if (r == null) {
            return;
        }
        if (r.nombre() != null) {
            if (r.nombre().isBlank()) {
                throw new BadRequestException("Ponga un nombre al sprint");
            }
            s.setNombre(recortar(r.nombre().trim(), 120));
        }
        if (s.getNombre() == null) {
            throw new BadRequestException("Ponga un nombre al sprint");
        }
        s.setObjetivo(r.objetivo() == null || r.objetivo().isBlank() ? null : recortar(r.objetivo().trim(), 500));
        s.setInicio(r.inicio());
        s.setFin(r.fin());
        fechasValidas(r.inicio(), r.fin());
        // El sprint vive dentro del plazo del proyecto.
        dentroDe(p.getInicio(), p.getFin(), r.inicio(), "El inicio del sprint", "del proyecto");
        dentroDe(p.getInicio(), p.getFin(), r.fin(), "El fin del sprint", "del proyecto");
    }

    /** Peso máximo que el proyecto reparte entre sus tarjetas (son porcentajes del avance). */
    public static final int PESO_TOTAL = 100;

    /**
     * Si una fecha cae fuera de [inicio, fin] (cualquiera de los dos puede faltar).
     * El mensaje dice qué fecha y de qué plazo se salió.
     */
    private static void dentroDe(LocalDate inicio, LocalDate fin, LocalDate fecha, String que, String deQue) {
        if (fecha == null) {
            return;
        }
        if (inicio != null && fecha.isBefore(inicio)) {
            throw new BadRequestException(que + " (" + fecha + ") es anterior al inicio " + deQue + " (" + inicio + ")");
        }
        if (fin != null && fecha.isAfter(fin)) {
            throw new BadRequestException(que + " (" + fecha + ") es posterior al fin " + deQue + " (" + fin + ")");
        }
    }

    // ================================================================== tablero

    public ItemVista crearItem(UUID id, ItemRequest r) {
        Ctx c = tablero(id);
        ProyectoItem i = new ProyectoItem();
        i.setProyectoId(id);
        i.setCreadoPor(c.u().getIdUser());
        i.setEstado(r != null && r.estado() != null && ESTADOS_ITEM.contains(r.estado()) ? r.estado() : "POR_HACER");
        i.setOrden(items.findByProyectoIdAndDeletedFalseOrderByOrdenAscCreatedAtAsc(id).size());
        aplicar(c, i, r);
        i = items.save(i);
        avisarResponsable(c, i, null);
        sincronizarEstado(c.p());
        return vista(i);
    }

    public ItemVista editarItem(UUID id, UUID itemId, ItemRequest r) {
        Ctx c = tablero(id);
        ProyectoItem i = itemDe(id, itemId);
        UUID antes = i.getResponsableId();
        aplicar(c, i, r);
        if (r != null && r.estado() != null) {
            estadoValido(r.estado());
            i.setEstado(r.estado());
        }
        i = items.save(i);
        avisarResponsable(c, i, antes);
        sincronizarEstado(c.p());
        return vista(i);
    }

    /** Mover una tarjeta de columna (arrastrar en el tablero). */
    public ItemVista moverItem(UUID id, UUID itemId, MoverRequest r) {
        Ctx c = tablero(id);
        ProyectoItem i = itemDe(id, itemId);
        if (r != null && r.estado() != null) {
            estadoValido(r.estado());
            i.setEstado(r.estado());
        }
        if (r != null && r.orden() != null) {
            i.setOrden(Math.max(0, r.orden()));
        }
        ItemVista v = vista(items.save(i));
        sincronizarEstado(c.p());
        return v;
    }

    /**
     * Terminar un proyecto exige que tenga tarjetas y que todas estén HECHO; si
     * no, el estado y el avance se contradicen ("Terminado" con 0 %).
     */
    private void exigirTerminado(UUID id) {
        List<ProyectoItem> l = items.findByProyectoIdAndDeletedFalseOrderByOrdenAscCreatedAtAsc(id);
        if (l.isEmpty()) {
            throw new BadRequestException("El proyecto no tiene tarjetas: agréguelas y termínelas antes de darlo por terminado");
        }
        long pendientes = l.stream().filter(x -> !"HECHO".equals(x.getEstado())).count();
        if (pendientes > 0) {
            throw new BadRequestException("Quedan " + pendientes + (pendientes == 1 ? " tarjeta" : " tarjetas")
                    + " sin terminar (avance " + avanceProyecto(l) + " %). Termínelas o cancele el proyecto.");
        }
        int avance = avanceProyecto(l);
        if (avance < PESO_TOTAL) {
            throw new BadRequestException("El avance es " + avance + " %: falta repartir " + (PESO_TOTAL - avance)
                    + " % del peso entre las tarjetas antes de darlo por terminado");
        }
    }

    /**
     * El estado sigue al tablero: un proyecto PLANIFICADO pasa a EN_CURSO cuando
     * una tarjeta empieza a moverse, y uno TERMINADO vuelve a EN_CURSO si se
     * añade o reabre trabajo. Terminarlo sigue siendo decisión del gestor.
     */
    private void sincronizarEstado(Proyecto p) {
        List<ProyectoItem> l = items.findByProyectoIdAndDeletedFalseOrderByOrdenAscCreatedAtAsc(p.getIdProyecto());
        boolean empezado = l.stream().anyMatch(x -> !"POR_HACER".equals(x.getEstado()));
        boolean pendiente = l.stream().anyMatch(x -> !"HECHO".equals(x.getEstado()));
        String nuevo = null;
        if ("PLANIFICADO".equals(p.getEstado()) && empezado) {
            nuevo = "EN_CURSO";
        } else if ("TERMINADO".equals(p.getEstado()) && pendiente) {
            nuevo = "EN_CURSO";
        }
        if (nuevo != null) {
            p.setEstado(nuevo);
            proyectos.save(p);
        }
    }

    public void eliminarItem(UUID id, UUID itemId) {
        tablero(id);
        ProyectoItem i = itemDe(id, itemId);
        i.setDeleted(true);
        items.save(i);
    }

    private ProyectoItem itemDe(UUID id, UUID itemId) {
        return items.findById(itemId)
                .filter(i -> id.equals(i.getProyectoId()) && !Boolean.TRUE.equals(i.getDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Tarjeta", "id", itemId));
    }

    private void aplicar(Ctx c, ProyectoItem i, ItemRequest r) {
        if (r == null) {
            throw new BadRequestException("Datos de la tarjeta vacíos");
        }
        if (r.titulo() != null) {
            if (r.titulo().isBlank()) {
                throw new BadRequestException("Ponga un título a la tarjeta");
            }
            i.setTitulo(recortar(r.titulo().trim(), 200));
        }
        if (i.getTitulo() == null) {
            throw new BadRequestException("Ponga un título a la tarjeta");
        }
        i.setDescripcion(r.descripcion() == null || r.descripcion().isBlank() ? null : r.descripcion());
        Integer pesoAntes = i.getPeso();
        if (r.peso() != null) {
            if (r.peso() < 1 || r.peso() > PESO_TOTAL) {
                throw new BadRequestException("El peso va de 1 a " + PESO_TOTAL);
            }
            i.setPeso(r.peso());
        }
        // Los pesos son porcentajes del avance: entre todas las tarjetas no pasan
        // de 100. Solo se comprueba al crear o al cambiar el peso, para no trabar
        // la edición de proyectos anteriores a esta regla.
        if (i.getIdProyectoItem() == null || !java.util.Objects.equals(pesoAntes, i.getPeso())) {
            int otros = items.findByProyectoIdAndDeletedFalseOrderByOrdenAscCreatedAtAsc(c.p().getIdProyecto()).stream()
                    .filter(x -> !x.getIdProyectoItem().equals(i.getIdProyectoItem()))
                    .mapToInt(x -> x.getPeso() == null ? 1 : x.getPeso()).sum();
            int peso = i.getPeso() == null ? 1 : i.getPeso();
            if (otros + peso > PESO_TOTAL) {
                int libre = Math.max(0, PESO_TOTAL - otros);
                throw new BadRequestException("Las tarjetas ya suman " + otros + "% del proyecto: a esta le "
                        + (libre == 0 ? "no queda peso (baje el de otra)" : "queda como máximo " + libre + "%"));
            }
        }
        // El responsable debe ser del equipo del proyecto.
        if (r.responsableId() != null && !miembros.existsByProyectoIdAndUserId(c.p().getIdProyecto(), r.responsableId())) {
            throw new BadRequestException("El responsable debe ser miembro del proyecto");
        }
        i.setResponsableId(r.responsableId());
        // La fecha límite cae dentro del proyecto y, si tiene sprint, dentro del sprint.
        dentroDe(c.p().getInicio(), c.p().getFin(), r.limite(), "La fecha límite", "del proyecto");
        if (r.sprintId() != null) {
            ProyectoSprint sp = sprintDe(c.p().getIdProyecto(), r.sprintId());
            dentroDe(sp.getInicio(), sp.getFin(), r.limite(), "La fecha límite", "del sprint \"" + sp.getNombre() + "\"");
        }
        i.setLimite(r.limite());
        i.setSprintId(r.sprintId());
    }

    private void avisarResponsable(Ctx c, ProyectoItem i, UUID antes) {
        UUID r = i.getResponsableId();
        if (r == null || r.equals(antes) || r.equals(c.u().getIdUser())) {
            return;
        }
        userRepository.findById(r).ifPresent(x -> notificationService.notifyProyecto(x, "PROYECTO_TAREA",
                "Tarea del proyecto " + c.p().getNombre() + ": " + i.getTitulo(),
                "<p><b>" + esc(nombre(c.u())) + "</b> le asignó la tarea <b>" + esc(i.getTitulo())
                        + "</b> del proyecto <b>" + esc(c.p().getNombre()) + "</b>"
                        + (i.getLimite() == null ? "" : ", para el " + i.getLimite()) + ".</p>",
                "proyectos/" + c.p().getIdProyecto()));
    }

    private static void estadoValido(String estado) {
        if (!ESTADOS_ITEM.contains(estado)) {
            throw new BadRequestException("Columna no válida: " + estado);
        }
    }

    // ================================================================== objetivos y logros

    public ObjetivoVista crearObjetivo(UUID id, ObjetivoRequest r) {
        gestor(id);
        if (r == null || r.descripcion() == null || r.descripcion().isBlank()) {
            throw new BadRequestException("Describa el objetivo");
        }
        ProyectoObjetivo o = new ProyectoObjetivo();
        o.setProyectoId(id);
        o.setDescripcion(recortar(r.descripcion().trim(), 500));
        o.setOrden(objetivos.findByProyectoIdAndDeletedFalseOrderByOrdenAscCreatedAtAsc(id).size());
        o = objetivos.save(o);
        return new ObjetivoVista(o.getIdProyectoObjetivo(), o.getDescripcion(), null, null);
    }

    public void editarObjetivo(UUID id, UUID objetivoId, ObjetivoRequest r) {
        gestor(id);
        if (r == null || r.descripcion() == null || r.descripcion().isBlank()) {
            throw new BadRequestException("Describa el objetivo");
        }
        ProyectoObjetivo o = objetivoDe(id, objetivoId);
        o.setDescripcion(recortar(r.descripcion().trim(), 500));
        objetivos.save(o);
    }

    /** Marca un objetivo como logrado (con nota) o lo devuelve a pendiente. */
    public void marcarLogro(UUID id, UUID objetivoId, LogroRequest r) {
        Ctx c = gestor(id);
        ProyectoObjetivo o = objetivoDe(id, objetivoId);
        boolean logrado = r != null && r.logrado();
        o.setLogradoAt(logrado ? OffsetDateTime.now() : null);
        o.setNotaLogro(logrado && r.nota() != null && !r.nota().isBlank() ? recortar(r.nota().trim(), 1000) : null);
        objetivos.save(o);
        if (logrado) {
            auditoria.record("PROYECTO_LOGRO", "Proyecto", id, c.u().getCorporateEmailUser(), o.getDescripcion());
        }
    }

    public void eliminarObjetivo(UUID id, UUID objetivoId) {
        gestor(id);
        ProyectoObjetivo o = objetivoDe(id, objetivoId);
        o.setDeleted(true);
        objetivos.save(o);
    }

    private ProyectoObjetivo objetivoDe(UUID id, UUID objetivoId) {
        return objetivos.findById(objetivoId)
                .filter(o -> id.equals(o.getProyectoId()) && !Boolean.TRUE.equals(o.getDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Objetivo", "id", objetivoId));
    }

    // ================================================================== utilidades

    /** Avance ponderado: pesos de las tarjetas HECHO sobre el total (0 si no hay tarjetas). */
    /**
     * Avance del proyecto: los pesos son % del proyecto (suman como máximo
     * PESO_TOTAL), así que el avance es la suma del peso de lo HECHO. Una sola
     * tarjeta de 40% terminada es 40%, no 100%.
     */
    static int avanceProyecto(Collection<ProyectoItem> lista) {
        int hecho = 0;
        for (ProyectoItem i : lista) {
            if ("HECHO".equals(i.getEstado())) {
                hecho += i.getPeso() == null ? 1 : i.getPeso();
            }
        }
        return Math.min(PESO_TOTAL, hecho);
    }

    /** Avance de un sprint: lo hecho sobre el peso de sus tarjetas. */
    static int avance(Collection<ProyectoItem> lista) {
        int total = 0;
        int hecho = 0;
        for (ProyectoItem i : lista) {
            int peso = i.getPeso() == null ? 1 : i.getPeso();
            total += peso;
            if ("HECHO".equals(i.getEstado())) {
                hecho += peso;
            }
        }
        return total == 0 ? 0 : Math.round(100f * hecho / total);
    }

    private List<Resumen> resumenes(List<Proyecto> lista, User u, UUID areaJefe) {
        if (lista.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = lista.stream().map(Proyecto::getIdProyecto).toList();
        Map<UUID, List<ProyectoItem>> porProyecto = items.findByProyectoIdInAndDeletedFalse(ids).stream()
                .collect(Collectors.groupingBy(ProyectoItem::getProyectoId));
        Map<UUID, Long> nMiembros = miembros.findByProyectoIdIn(ids).stream()
                .collect(Collectors.groupingBy(ProyectoMiembro::getProyectoId, Collectors.counting()));
        Map<UUID, List<ProyectoArea>> pas = areasProyecto.findByProyectoIdIn(ids).stream()
                .collect(Collectors.groupingBy(ProyectoArea::getProyectoId));
        Map<UUID, String> areas = nombresDeAreas();
        boolean superAdmin = Roles.esSuperAdmin(rol(u));
        return lista.stream()
                .map(p -> resumen(p, porProyecto.getOrDefault(p.getIdProyecto(), List.of()),
                        nMiembros.getOrDefault(p.getIdProyecto(), 0L).intValue(),
                        pas.getOrDefault(p.getIdProyecto(), List.of()), areas,
                        superAdmin || Objects.equals(areaJefe, p.getAreaId())))
                .toList();
    }

    private static Resumen resumen(Proyecto p, List<ProyectoItem> its, int nMiembros, List<ProyectoArea> pas,
                                   Map<UUID, String> areas, boolean gestionar) {
        int hechas = (int) its.stream().filter(i -> "HECHO".equals(i.getEstado())).count();
        List<String> participantes = pas.stream().filter(pa -> "ACEPTADA".equals(pa.getEstado()))
                .map(pa -> areas.getOrDefault(pa.getAreaId(), "—")).toList();
        return new Resumen(p.getIdProyecto(), p.getNombre(), p.getDescripcion(), p.getAreaId(),
                areas.getOrDefault(p.getAreaId(), "—"), p.getEstado(), p.getIcono(), p.getColor(), p.getInicio(),
                p.getFin(), avanceProyecto(its), its.size(), hechas, nMiembros, participantes, gestionar, p.getUpdatedAt());
    }

    private ItemVista vista(ProyectoItem i) {
        String responsable = i.getResponsableId() == null ? null
                : nombre(userRepository.findById(i.getResponsableId()).orElse(null));
        return new ItemVista(i.getIdProyectoItem(), i.getTitulo(), i.getDescripcion(), i.getEstado(), i.getPeso(),
                i.getResponsableId(), responsable, i.getLimite(), i.getOrden(), i.getSprintId());
    }

    private static void aplicar(Proyecto p, ProyectoRequest r) {
        if (r == null) {
            throw new BadRequestException("Datos del proyecto vacíos");
        }
        if (r.nombre() != null) {
            if (r.nombre().isBlank()) {
                throw new BadRequestException("Ponga un nombre al proyecto");
            }
            p.setNombre(recortar(r.nombre().trim(), 150));
        }
        if (p.getNombre() == null) {
            throw new BadRequestException("Ponga un nombre al proyecto");
        }
        p.setDescripcion(r.descripcion() == null || r.descripcion().isBlank() ? null : r.descripcion().trim());
        p.setIcono(r.icono() != null && r.icono().matches("^[a-z0-9 -]{3,100}$") ? r.icono().trim() : p.getIcono());
        p.setColor(r.color() != null && r.color().matches("^#[0-9A-Fa-f]{6}$") ? r.color() : p.getColor());
        fechasValidas(r.inicio(), r.fin());
        p.setInicio(r.inicio());
        p.setFin(r.fin());
    }

    private static void fechasValidas(LocalDate inicio, LocalDate fin) {
        if (inicio != null && fin != null && fin.isBefore(inicio)) {
            throw new BadRequestException("La fecha de fin es anterior a la de inicio");
        }
    }

    private Area areaValida(UUID areaId) {
        return areaRepository.findById(areaId)
                .filter(a -> !Boolean.TRUE.equals(a.getDeletedArea()))
                .orElseThrow(() -> new BadRequestException("Área no válida"));
    }

    private Map<UUID, String> nombresDeAreas() {
        Map<UUID, String> m = new HashMap<>();
        areaRepository.findAll().forEach(a -> m.put(a.getIdArea(), a.getNameArea()));
        return m;
    }

    private static UUID areaDe(User u) {
        return u == null || u.getArea() == null ? null : u.getArea().getIdArea();
    }

    private static String nombre(User u) {
        if (u == null) {
            return "—";
        }
        return u.getDisplayName() != null && !u.getDisplayName().isBlank() ? u.getDisplayName() : u.getCorporateEmailUser();
    }

    private static <T> Map<UUID, T> porId(Iterable<T> lista, Function<T, UUID> id) {
        Map<UUID, T> m = new HashMap<>();
        lista.forEach(x -> m.put(id.apply(x), x));
        return m;
    }

    private static String recortar(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
