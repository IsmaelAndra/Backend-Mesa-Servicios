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
    private void comprobarPuedeAsignarRol(Role role) {
        // ADMIN quedó dado de baja en V27: sus cuentas pasaron a jefe de área.
        if (Boolean.TRUE.equals(role.getDeletedRole())) {
            throw new BadRequestException("El rol " + role.getNameRole() + " ya no existe");
        }
        // Jefe, agente y usuario los asigna también un jefe que gestiona usuarios
        // (quién gestiona ya lo comprobó exigirGestionDeUsuarios). Súper admin y
        // auditor, solo un súper admin.
        if (com.mesaayuda.security.Roles.ASIGNABLES_POR_JEFE.contains(role.getNameRole())) {
            return;
        }
        if (!hasRole(ROL_SUPER_ADMIN)) {
            log.warn("Intento de otorgar el rol {} por un usuario sin ese privilegio.", role.getNameRole());
            throw new AccessDeniedException("Solo un SUPER_ADMIN puede asignar el rol " + role.getNameRole());
        }
    }

    /**
     * Crear, editar, activar o eliminar cuentas: el súper admin y los jefes de
     * las áreas marcadas como "gestiona usuarios" (Tecnología y Desarrollo).
     */
    @Transactional(readOnly = true)
    public void exigirGestionDeUsuarios() {
        if (!puedeGestionarUsuarios(getCurrentUser())) {
            throw new AccessDeniedException("No tiene permiso para gestionar usuarios");
        }
    }

    /** Consultar cuentas: quien las gestiona y el auditor (solo lectura). */
    @Transactional(readOnly = true)
    public void exigirLecturaDeUsuarios() {
        User actual = getCurrentUser();
        boolean auditor = actual.getRolUser() != null
                && com.mesaayuda.security.Roles.esAuditor(actual.getRolUser().getNameRole());
        if (!auditor && !puedeGestionarUsuarios(actual)) {
            throw new AccessDeniedException("No tiene permiso para consultar usuarios");
        }
    }

    public static boolean puedeGestionarUsuarios(User u) {
        if (u == null || u.getRolUser() == null) {
            return false;
        }
        String rol = u.getRolUser().getNameRole();
        if (com.mesaayuda.security.Roles.esSuperAdmin(rol)) {
            return true;
        }
        return com.mesaayuda.security.Roles.esJefe(rol)
                && u.getArea() != null
                && Boolean.TRUE.equals(u.getArea().getManagesUsersArea());
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
                        estados.getOrDefault(u.getIdUser(), "DESCONECTADO"),
                        u.getExtensionUser()))
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
     * Sucursal de la persona. Opcional a proposito: si no se elige, o si la sucursal ya
     * no existe, la cuenta se queda sin sucursal en vez de fallar el alta.
     */
    private Sucursal resolverSucursal(UUID sucursalId) {
        if (sucursalId == null) {
            return null;
        }
        return sucursalRepository.findById(sucursalId)
                .filter(s -> !s.getDeletedSucursal())
                .orElse(null);
    }
    /**
     * El alta desde Usuarios pide lo que imprime un acta de activos y Graph no
     * trae: cédula, cargo y extensión (el nombre sale del correo vía Graph, o de
     * la parte antes de la @). El registro propio y Graph no pasan por aquí.
     */
    public static void exigirDatosDelAlta(RegisterRequest r) {
        java.util.List<String> faltan = new java.util.ArrayList<>();
        if (r.identification() == null || r.identification().isBlank()) faltan.add("cédula");
        if (r.cargo() == null || r.cargo().isBlank()) faltan.add("cargo");
        if (r.extension() == null || r.extension().isBlank()) faltan.add("extensión");
        if (!faltan.isEmpty()) {
            throw new BadRequestException("Faltan datos obligatorios: " + String.join(", ", faltan));
        }
        if (!r.identification().trim().matches("^[0-9]{10}$")) {
            throw new BadRequestException("La cédula debe tener 10 números");
        }
    }

    /**
     * Teléfono: dígitos con +, espacios, guiones o paréntesis, y entre 7 y 15
     * dígitos. Vacío = sin teléfono (null). Antes se aceptaba "abc".
     */
    public static String telefonoValido(String telefono) {
        if (telefono == null || telefono.isBlank()) return null;
        String t = telefono.trim();
        long digitos = t.chars().filter(Character::isDigit).count();
        if (!t.matches("^[0-9+()\\-\\s]+$") || digitos < 7 || digitos > 15) {
            throw new BadRequestException("Teléfono no válido: use solo números (7 a 15 dígitos)");
        }
        return t;
    }

    /** "superadmin@coacmagisterio.fin.ec" → "superadmin": nombre de las cuentas que Graph no conoce. */
    public static String nombreDesdeCorreo(String correo) {
        if (correo == null || correo.isBlank()) return null;
        String local = correo.split("@")[0].trim();
        return local.isEmpty() ? correo : local;
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
        comprobarPuedeAsignarRol(role);
        Area area = areaRepository.findById(request.areaId())
                .orElseThrow(() -> new BadRequestException("Área inválida"));
        exigirUnSoloJefe(role, area, null);

        String providedPassword = request.password() == null ? "" : request.password().trim();
        String generatedPassword = providedPassword.isBlank() ? PasswordGenerator.generate() : providedPassword;

        User user = new User();
        user.setIdentificationUser(identification);
        user.setCorporateEmailUser(request.corporateEmail());
        user.setPasswordUser(passwordEncoder.encode(generatedPassword));
        user.setPhoneUser(telefonoValido(request.phone()));
        user.setExtensionUser(extensionLimpia(request.extension()));
        user.setCargoUser(request.cargo() == null || request.cargo().isBlank() ? null : request.cargo().trim());
        if (request.nombre() != null && !request.nombre().isBlank()) {
            String nombre = request.nombre().trim().replaceAll("\\s+", " ");
            user.setDisplayName(nombre);
            // Primer bloque = nombres, el resto = apellidos (Graph lo corrige si conoce a la persona).
            String[] partes = nombre.split(" ");
            int mitad = partes.length >= 4 ? 2 : 1;
            user.setFirstName(String.join(" ", java.util.Arrays.copyOfRange(partes, 0, Math.min(mitad, partes.length))));
            user.setLastName(partes.length > mitad ? String.join(" ", java.util.Arrays.copyOfRange(partes, mitad, partes.length)) : null);
        }
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
        // Sin nombre de Graph (cuentas genéricas como superadmin@): la parte antes de la @.
        saved = userRepository.findById(saved.getIdUser()).orElse(saved);
        if (saved.getDisplayName() == null || saved.getDisplayName().isBlank()) {
            saved.setDisplayName(nombreDesdeCorreo(saved.getCorporateEmailUser()));
            saved = userRepository.save(saved);
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
            if (!request.phoneUser().trim().equals(java.util.Objects.toString(user.getPhoneUser(), ""))) {
                user.setPhoneUser(telefonoValido(request.phoneUser()));
            }
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
        comprobarPuedeAsignarRol(role);
        Area area = areaRepository.findById(request.areaId())
                .orElseThrow(() -> new BadRequestException("Área inválida"));
        // Solo si pasa a ser jefe o cambia de área: editar otros datos de un jefe
        // que ya compartía área (datos anteriores a esta regla) no se bloquea.
        boolean pasaAJefe = !role.getNameRole().equals(user.getRolUser() == null ? null : user.getRolUser().getNameRole());
        boolean cambiaArea = user.getArea() == null || !user.getArea().getIdArea().equals(area.getIdArea());
        if ("ACTIVE".equals(user.getStatusUser()) && (pasaAJefe || cambiaArea)) {
            exigirUnSoloJefe(role, area, user.getIdUser());
        }

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
        if (request.phone() != null && !request.phone().trim().equals(java.util.Objects.toString(user.getPhoneUser(), ""))) {
            user.setPhoneUser(telefonoValido(request.phone()));
        }
        if (request.extension() != null) {
            // Vacía = se le quita la extensión.
            user.setExtensionUser(extensionLimpia(request.extension()));
        }
        if (request.cargo() != null) {
            user.setCargoUser(request.cargo().isBlank() ? null : request.cargo().trim());
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
      // Las cuentas de súper admin y de auditor solo las toca un súper admin:
      // un jefe que gestiona usuarios no puede degradarlas ni desactivarlas.
      String rolObjetivo = objetivo.getRolUser().getNameRole();
      if (!com.mesaayuda.security.Roles.esSuperAdmin(rolObjetivo)
              && !com.mesaayuda.security.Roles.esAuditor(rolObjetivo)) {
          return;
      }
      if (hasRole(ROL_SUPER_ADMIN)) {
          return;
      }
      log.warn("Intento de {} sobre una cuenta {} por un usuario sin ese privilegio.",
              operacion, rolObjetivo);
      throw new AccessDeniedException(
              "Solo un SUPER_ADMIN puede " + operacion + " una cuenta " + rolObjetivo);
  }

  /**
   * Un área tiene un solo jefe activo: el jefe responde por la de su cuenta
   * (V33) y con dos, avisos, aprobaciones y accesos confidenciales se repartían
   * sin criterio. {@code propio} es la cuenta que se edita (no choca consigo misma).
   */
  void exigirUnSoloJefe(Role role, Area area, UUID propio) {
      if (role == null || area == null || !com.mesaayuda.security.Roles.JEFE.equals(role.getNameRole())) {
          return;
      }
      userRepository.jefesActivosDe(area.getIdArea()).stream()
              .filter(j -> !j.getIdUser().equals(propio))
              .findFirst()
              .ifPresent(j -> {
                  throw new BadRequestException("El área " + area.getNameArea() + " ya tiene jefe: "
                          + (j.getDisplayName() != null && !j.getDisplayName().isBlank() ? j.getDisplayName() : j.getCorporateEmailUser())
                          + ". Cambie primero su rol o su área.");
              });
  }

  /** Si la cuenta es la de quien hace la petición (sin sesión, no). */
  private boolean esUnoMismo(User user) {
      var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
      return auth != null && auth.getName() != null && user.getCorporateEmailUser() != null
              && auth.getName().equalsIgnoreCase(user.getCorporateEmailUser());
  }

  public void delete(UUID id) {
      User user = getUser(id);
      if (esUnoMismo(user)) {
          throw new BadRequestException("No puede eliminar su propia cuenta");
      }
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
      // Nadie se desactiva a sí mismo: se quedaría fuera sin poder volver.
      if (esUnoMismo(user) && "INACTIVE".equals(status)) {
          throw new BadRequestException("No puede desactivar su propia cuenta");
      }
      if ("ACTIVE".equals(status) && !"ACTIVE".equals(estadoPrevio)) {
          exigirUnSoloJefe(user.getRolUser(), user.getArea(), user.getIdUser());
      }
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

    public User getUser(UUID id) {
        return userRepository.findById(id)
                .filter(u -> !u.getDeletedUser())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", "id", id));
    }
}
