package com.mesaayuda.service;

import com.mesaayuda.dto.ProfileRequest;
import com.mesaayuda.dto.RegisterRequest;
import com.mesaayuda.dto.AgentOptionResponse;
import com.mesaayuda.dto.UserResponse;
import com.mesaayuda.entity.Sucursal;
import com.mesaayuda.entity.Area;
import com.mesaayuda.entity.Role;
import com.mesaayuda.entity.User;
import com.mesaayuda.exception.BadRequestException;
import com.mesaayuda.exception.ResourceNotFoundException;
import com.mesaayuda.mapper.UserMapper;
import com.mesaayuda.repository.SucursalRepository;
import com.mesaayuda.repository.AreaRepository;
import com.mesaayuda.repository.RoleRepository;
import com.mesaayuda.repository.UserRepository;
import com.mesaayuda.util.PasswordGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Predicate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional
public class UserService {

    private static final long TEMPORAL_PASSWORD_MINUTES = 24 * 60;
    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    /** Lazy por la misma razón que en SlaService: evita un ciclo con el motor de workflows. */
    @Autowired
    @org.springframework.context.annotation.Lazy
    private com.mesaayuda.workflow.WorkflowEventPublisher workflowEventPublisher;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private AreaRepository areaRepository;

    @Autowired
    private SucursalRepository sucursalRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private SystemAuditService systemAuditService;

    @Autowired
    private MicrosoftGraphUserSyncService microsoftGraphUserSyncService;

    @Autowired
    private PresenceService presenceService;

    @Autowired
    private com.mesaayuda.repository.RefreshTokenRepository refreshTokenRepository;

    private static final String ROL_SUPER_ADMIN = "SUPER_ADMIN";

    /**
     * Impide que alguien con rol ADMIN se conceda a sí mismo (o a otro) el rol
     * SUPER_ADMIN. Antes el rol venía del cuerpo de la petición sin ninguna
     * comprobación, de modo que un ADMIN podía llamarse a sí mismo y quedaba
     * por encima de las restricciones que se aplican al resto de
     * administradores. Solo un SUPER_ADMIN puede crear o promover a otro
     * SUPER_ADMIN.
     */
    private void comprobarPuedeAsignarRol(String roleName) {
        if (!ROL_SUPER_ADMIN.equalsIgnoreCase(roleName)) {
            return;
        }
        if (!hasRole(ROL_SUPER_ADMIN)) {
            log.warn("Intento de otorgar el rol SUPER_ADMIN por un usuario sin ese privilegio.");
            throw new AccessDeniedException("Solo un SUPER_ADMIN puede crear o promover a otro SUPER_ADMIN");
        }
    }

    /**
     * Invalida las sesiones activas del usuario. Se llama cuando cambia su rol,
     * su estado o se le elimina: si no, el token de refresco que ya tenía
     * seguía siendo válido y podía renovar la sesión indefinidamente, incluso
     * con la cuenta desactivada o el rol rebajado.
     */
    private void revocarSesiones(UUID userId) {
        try {
            refreshTokenRepository.revokeAllByUser(userId);
            userRepository.invalidarTokensDeAcceso(userId, OffsetDateTime.now());
        } catch (Exception e) {
            log.warn("No se pudieron revocar las sesiones del usuario {}: {}", userId, e.getMessage());
        }
    }

    /**
     * Añade el estado de sesión (EN_LINEA / RECIENTE / DESCONECTADO) a una lista de
     * usuarios, resolviendo todos los estados en una sola consulta.
     */
    private List<UserResponse> conSesion(List<UserResponse> usuarios) {
        if (usuarios == null || usuarios.isEmpty()) {
            return usuarios;
        }
        Map<UUID, String> estados = presenceService.estadosDe(
                usuarios.stream().map(UserResponse::idUser).filter(Objects::nonNull).toList());
        return usuarios.stream()
                .map(u -> {
                    if (u == null) {
                        return null;
                    }
                    String estado = u.idUser() == null
                            ? PresenceService.DESCONECTADO
                            : estados.getOrDefault(u.idUser(), PresenceService.DESCONECTADO);
                    return u.withSessionState(estado);
                })
                .toList();
    }

    private UserResponse conSesion(UserResponse usuario) {
        if (usuario == null) {
            return null;
        }
        return usuario.withSessionState(presenceService.estadoDe(usuario.idUser()));
    }

    /** Deriva la identificación desde el correo corporativo cuando el usuario (ya no) la proporciona. */
    private String resolveIdentification(String identification, String corporateEmail) {
        if (identification != null && !identification.isBlank()) {
            return identification.trim();
        }
        String local = corporateEmail != null ? corporateEmail.split("@")[0].trim() : "";
        return local.length() > 30 ? local.substring(0, 30) : local;
    }

    /**
     * Agentes activos, para el desplegable de asignacion del kanban.
     *
     * <p>Devuelve un DTO reducido en lugar del UserResponse completo. Antes se
     * servia entero y, sin comprobacion de rol, cualquier usuario con sesion se
     * llevaba por esta via la identificacion, el telefono y el UPN de Microsoft de
     * todo el personal. Ahora el endpoint exige rol AGENT, ADMIN o SUPER_ADMIN, que
     * es lo unico que el kanban necesita.
     */
    @Transactional(readOnly = true)
    public List<AgentOptionResponse> findAgentOptions() {
        List<User> agentes = userRepository
                .findByRolUserNameRoleAndStatusUserAndDeletedUserFalseOrderByCreatedAtUserAsc("AGENT", "ACTIVE");
        // Una sola consulta de presencia para toda la lista: llamarla por agente
        // seria una consulta por cada fila del desplegable.
        Map<UUID, String> estados = presenceService.estadosDe(
                agentes.stream().map(User::getIdUser).toList());
        return agentes.stream()
                // El tipo se declara de forma explicita porque el ternario del
                // nombre hace que el compilador infiera Object y .toList() no
                // cuadre con List<AgentOptionResponse>.
                .<AgentOptionResponse>map(u -> new AgentOptionResponse(
                        u.getIdUser(),
                        u.getDisplayName() != null && !u.getDisplayName().isBlank()
                                ? u.getDisplayName()
                                : u.getCorporateEmailUser(),
                        u.getCorporateEmailUser(),
                        u.getPhotoUrl(),
                        estados.getOrDefault(u.getIdUser(), "DESCONECTADO")))
                .toList();
    }
    @Transactional(readOnly = true)
  public List<UserResponse> findAll(String role, String status, String search) {
        Specification<User> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("deletedUser")));
            if (role != null && !role.isBlank()) {
                predicates.add(cb.equal(root.get("rolUser").get("nameRole"), role.toUpperCase()));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("statusUser"), status.toUpperCase()));
            }
            if (search != null && !search.isBlank()) {
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("corporateEmailUser")), "%" + search.toLowerCase() + "%"),
                        cb.like(root.get("identificationUser"), "%" + search + "%")
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        // Sin ORDER BY PostgreSQL devuelve las filas en orden fisico, que cambia
        // cada vez que se actualiza una fila (por ejemplo al subir una foto): en
        // los desplegables de asignacion se veian los usuarios con foto juntos
        // delante y el resto detras. Se ordena por nombre y luego por correo,
        // que es unico, para que el orden sea siempre el mismo.
        Sort orden = Sort.by(
                Sort.Order.asc("displayName").nullsLast(),
                Sort.Order.asc("corporateEmailUser"));
        return conSesion(userRepository.findAll(spec, orden).stream().map(userMapper::toResponse).toList());
    }

    @Transactional(readOnly = true)
    public UserResponse findById(UUID id) {
        return conSesion(userMapper.toResponse(getUser(id)));
    }

    /**
     * Consulta libre con limitación para la búsqueda global.
     *
     * <p>Único camino por el que el personal puede listar personas; el spec lo
     * construye {@link SearchService} según el rol pedido (USER o AGENT) y el
     * término, y el tope lo impone el {@code Pageable} para no traer el censo
     * entero a memoria.
     */
    @Transactional(readOnly = true)
    public Page<User> findAllPorSpec(Specification<User> spec, Pageable pageable) {
        return userRepository.findAll(spec, pageable);
    }

    /**
     * Sede de la persona. Opcional a proposito: si no se elige, o si la sede ya
     * no existe, la cuenta se queda sin sede en vez de fallar el alta.
     */
    private Sucursal resolverSucursal(UUID sucursalId) {
        if (sucursalId == null) {
            return null;
        }
        return sucursalRepository.findById(sucursalId)
                .filter(s -> !s.getDeletedSucursal())
                .orElse(null);
    }
    public UserResponse create(RegisterRequest request) {
        String identification = resolveIdentification(request.identification(), request.corporateEmail());
        if (userRepository.existsByCorporateEmailUser(request.corporateEmail())) {
            throw new BadRequestException("El correo corporativo ya está registrado");
        }
        if (userRepository.existsByIdentificationUser(identification)) {
            throw new BadRequestException("La identificación ya está registrada");
        }

        Role role = roleRepository.findByNameRole(request.roleName().toUpperCase())
                .orElseThrow(() -> new BadRequestException("Rol inválido: " + request.roleName()));
        comprobarPuedeAsignarRol(role.getNameRole());
        Area area = areaRepository.findById(request.areaId())
                .orElseThrow(() -> new BadRequestException("Área inválida"));

        String providedPassword = request.password() == null ? "" : request.password().trim();
        String generatedPassword = providedPassword.isBlank() ? PasswordGenerator.generate() : providedPassword;

        User user = new User();
        user.setIdentificationUser(identification);
        user.setCorporateEmailUser(request.corporateEmail());
        user.setPasswordUser(passwordEncoder.encode(generatedPassword));
        user.setPhoneUser(request.phone());
        user.setExtensionUser(extensionLimpia(request.extension()));
        user.setStatusUser("ACTIVE");
        user.setRolUser(role);
        user.setArea(area);
        user.setSucursal(resolverSucursal(request.sucursalId()));
        user.setDeletedUser(false);
        user.setMustChangePassword(true);
        user.setTemporalPasswordExpiresAt(OffsetDateTime.now().plusMinutes(TEMPORAL_PASSWORD_MINUTES));
        User saved = userRepository.save(user);

        try {
            // Nombres y foto los trae Microsoft Graph; si no está configurado no hace nada.
            microsoftGraphUserSyncService.syncUser(saved.getIdUser(), true);
        } catch (Exception e) {
            log.warn("No se pudo sincronizar el usuario nuevo con Microsoft Graph: {}", e.getMessage());
        }

        systemAuditService.record("USER_CREATE", "User", saved.getIdUser());
        workflowEventPublisher.usuarioCreado(saved);
        notificationService.notifyNewAccount(saved, generatedPassword);
        log.info("Usuario creado por administración: {}", saved.getCorporateEmailUser());

        return userMapper.toResponse(saved);
    }

    /**
     * Actualiza el perfil del usuario con sesion, sin pasar por administracion.
     *
     * El perfil se guardaba contra update(id, RegisterRequest), que exige
     * roleName y areaId: el formulario no manda eso y el backend devolvia 400.
     * Aqui se tocan unicamente los campos que el usuario controla sobre si
     * mismo; el rol, el area y el estado siguen siendo de administracion.
     */
    @Transactional
    public UserResponse updateMyProfile(ProfileRequest request) {
        User user = getCurrentUser();

        if (request.identificationUser() != null && !request.identificationUser().isBlank()) {
            String identificacion = request.identificationUser().trim();
            if (!identificacion.equalsIgnoreCase(user.getIdentificationUser())
                    && userRepository.existsByIdentificationUser(identificacion)) {
                throw new BadRequestException("La identificación ya está registrada");
            }
            user.setIdentificationUser(identificacion);
        }
        if (request.phoneUser() != null) {
            user.setPhoneUser(request.phoneUser().isBlank() ? null : request.phoneUser().trim());
        }
        if (request.firstName() != null) {
            user.setFirstName(blankToNull(request.firstName()));
        }
        if (request.lastName() != null) {
            user.setLastName(blankToNull(request.lastName()));
        }
        if (request.displayName() != null) {
            user.setDisplayName(blankToNull(request.displayName()));
        }

        systemAuditService.record("PROFILE_UPDATE", "User", user.getIdUser());
        return conSesion(userMapper.toResponse(userRepository.save(user)));
    }

    private String blankToNull(String valor) {
        String v = valor == null ? null : valor.trim();
        return v == null || v.isEmpty() ? null : v;
    }

public UserResponse update(UUID id, RegisterRequest request) {
      User user = getUser(id);
      exigirSuperAdminParaSuperAdmin(user, "editar");
      String identification = resolveIdentification(request.identification(),
                request.corporateEmail() != null && !request.corporateEmail().isBlank() ? request.corporateEmail() : user.getCorporateEmailUser());

        if (!user.getCorporateEmailUser().equalsIgnoreCase(request.corporateEmail())
                && userRepository.existsByCorporateEmailUser(request.corporateEmail())) {
            throw new BadRequestException("El correo corporativo ya está registrado");
        }
        if (!user.getIdentificationUser().equalsIgnoreCase(identification)
                && userRepository.existsByIdentificationUser(identification)) {
            throw new BadRequestException("La identificación ya está registrada");
        }

        Role role = roleRepository.findByNameRole(request.roleName().toUpperCase())
                .orElseThrow(() -> new BadRequestException("Rol inválido"));
        comprobarPuedeAsignarRol(role.getNameRole());
        Area area = areaRepository.findById(request.areaId())
                .orElseThrow(() -> new BadRequestException("Área inválida"));

        // Si el rol o la contraseña cambian, las sesiones que ya tenía abiertas
        // dejan de ser válidas: si no, un token de refresco emitido con el rol
        // anterior seguiría renovando la sesión indefinidamente.
        boolean cambiaRol = !user.getRolUser().getNameRole().equals(role.getNameRole());
        boolean cambiaPassword = request.password() != null && !request.password().isBlank();

        user.setIdentificationUser(identification);
        user.setCorporateEmailUser(request.corporateEmail());
        if (request.password() != null && !request.password().isBlank()) {
            user.setPasswordUser(passwordEncoder.encode(request.password()));
            user.setMustChangePassword(true);
            user.setTemporalPasswordExpiresAt(OffsetDateTime.now().plusMinutes(TEMPORAL_PASSWORD_MINUTES));
        }
        if (request.phone() != null) {
            user.setPhoneUser(request.phone());
        }
        if (request.extension() != null) {
            // Vacía = se le quita la extensión.
            user.setExtensionUser(extensionLimpia(request.extension()));
        }
        user.setRolUser(role);
        user.setArea(area);
        user.setSucursal(resolverSucursal(request.sucursalId()));
        UserResponse respuesta = conSesion(userMapper.toResponse(userRepository.save(user)));
        if (cambiaRol || cambiaPassword) {
            revocarSesiones(user.getIdUser());
        }
        systemAuditService.record("USER_UPDATE", "User", user.getIdUser());
        workflowEventPublisher.usuarioActualizado(user);
        return respuesta;
    }

/**
     * Impide que alguien actúe por encima de su nivel.
     *
     * <p>El control de acceso por rol protege contra un ADMIN que sube a
     * SUPER_ADMIN, pero no contra el caso inverso: un ADMIN que baja a otro
     * SUPER_ADMIN, le cambia la contraseña o lo desactiva. Como los tres métodos
     * de gestión de usuarios exigen solo {@code checkAdminOrSuperAdmin()}, un
     * ADMIN podía tomar posesion de la cuenta de un SUPER_ADMIN fijandole una
     * contraseña que él conoce e iniciar sesión con ella. La cuenta conservaba su
     * rol, así que el atacante entraba directamente como SUPER_ADMIN.
     *
     * <p>La regla es de no-regresión, no de igual nivel: nadie, en ningún caso,
     * toca a una cuenta de SUPER_ADMIN sin serlo. Se aplica a contraseña, rol y
     * estado, que son los tres caminos por los que cambia el nivel de una cuenta.
     *
     * @param objetivo usuario sobre el que se va a operar
     * @param operacion descripción para el mensaje de error y el log
     */
private void exigirSuperAdminParaSuperAdmin(User objetivo, String operacion) {
      if (objetivo == null || objetivo.getRolUser() == null) {
          return;
      }
      if (!ROL_SUPER_ADMIN.equalsIgnoreCase(objetivo.getRolUser().getNameRole())) {
          return;
      }
      if (hasRole(ROL_SUPER_ADMIN)) {
          return;
      }
      log.warn("Intento de {} sobre una cuenta SUPER_ADMIN por un usuario sin ese privilegio.",
              operacion);
      throw new AccessDeniedException(
              "Solo un SUPER_ADMIN puede " + operacion + " a otro SUPER_ADMIN");
  }

  public void delete(UUID id) {
      User user = getUser(id);
      exigirSuperAdminParaSuperAdmin(user, "eliminar");
      user.setDeletedUser(true);
        user.setStatusUser("INACTIVE");
        userRepository.save(user);
        // Una cuenta eliminada no debe poder renovar su sesión.
        revocarSesiones(user.getIdUser());
        systemAuditService.record("USER_DELETE", "User", user.getIdUser());
        workflowEventPublisher.usuarioDesactivado(user, "ACTIVE");
    }

public UserResponse changeStatus(UUID id, String status) {
      User user = getUser(id);
      exigirSuperAdminParaSuperAdmin(user, "cambiar el estado de");
      // Solo los dos valores que entiende el resto del sistema. Con texto libre,
      // un valor como "active " o "BLOQUEADO" dejaba la cuenta fuera de juego
      // sin que la pantalla supiera mostrar en qué estado quedó.
      String normalizado = status == null ? "" : status.trim().toUpperCase();
      if (!"ACTIVE".equals(normalizado) && !"INACTIVE".equals(normalizado)) {
          throw new BadRequestException("Estado inválido: use ACTIVE o INACTIVE");
      }
      status = normalizado;
      // Se lee antes de tocar la entidad: si se preguntara despues devolveria
      // el valor nuevo y la auditoria diria "ACTIVE -> ACTIVE".
      String estadoPrevio = user.getStatusUser();
      user.setStatusUser(status);
      UserResponse respuesta = conSesion(userMapper.toResponse(userRepository.save(user)));
      // Desactivar a alguien tiene que cerrar sus sesiones abiertas; si no,
      // su token de refresco seguiría siendo válido y podría volver a entrar.
      revocarSesiones(user.getIdUser());
      // Sin este registro, desactivar una cuenta no dejaba rastro en ningun sitio:
      // ni en audit_log ni en el log de aplicacion. Es una accion critica.
      // Con cinco argumentos: la versión de cuatro toma el cuarto como USUARIO, y
      // antes "ACTIVE -> INACTIVE" acababa en la columna de quién lo hizo.
      Authentication actor = SecurityContextHolder.getContext().getAuthentication();
      systemAuditService.record("USER_STATUS_CHANGE", "User", user.getIdUser(),
              actor == null ? null : actor.getName(),
              (estadoPrevio == null ? "(vacio)" : estadoPrevio) + " -> " + status);
      if ("INACTIVE".equals(status) && !"INACTIVE".equals(estadoPrevio)) {
          workflowEventPublisher.usuarioDesactivado(user, estadoPrevio);
      } else {
          workflowEventPublisher.usuarioActualizado(user);
      }
      return respuesta;
  }

    @Transactional(readOnly = true)
    /** Extensión sin espacios; vacía o con letras se guarda como "sin extensión". */
    private static String extensionLimpia(String extension) {
        if (extension == null) {
            return null;
        }
        String e = extension.trim();
        return e.matches("^[0-9]{1,10}$") ? e : null;
    }

    public User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new BadRequestException("No autenticado");
        }
        return userRepository.findByCorporateEmailUser(auth.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", "correo", auth.getName()));
    }

    @Transactional(readOnly = true)
    public boolean hasRole(String roleName) {
        User actual = getCurrentUser();
        // Un usuario sin rol no es nada de lo que se le pueda pedir: se
        // comprueba en varias autorizaciones, y reventar aquí sería devolver un
        // 500 en lugar de denegar (403).
        return actual != null && actual.getRolUser() != null
                && roleName.equalsIgnoreCase(actual.getRolUser().getNameRole());
    }

    @Transactional(readOnly = true)
    public List<String> findAgentEmails() {
        return userRepository.findByRoleName("AGENT").stream()
                .map(User::getCorporateEmailUser)
                .toList();
    }

    private User getUser(UUID id) {
        return userRepository.findById(id)
                .filter(u -> !u.getDeletedUser())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", "id", id));
    }
}
