package com.mesaayuda.service;

import com.mesaayuda.dto.AssetAssignableUserResponse;
import com.mesaayuda.dto.AssetHistoryResponse;
import com.mesaayuda.dto.AssetRequest;
import com.mesaayuda.dto.AssetResponse;
import com.mesaayuda.entity.Area;
import com.mesaayuda.entity.Asset;
import com.mesaayuda.entity.AssetHistory;
import com.mesaayuda.entity.Sucursal;
import com.mesaayuda.entity.User;
import com.mesaayuda.exception.BadRequestException;
import com.mesaayuda.exception.ResourceNotFoundException;
import com.mesaayuda.mapper.AssetHistoryMapper;
import com.mesaayuda.mapper.AssetMapper;
import com.mesaayuda.repository.AreaRepository;
import com.mesaayuda.repository.AssetHistoryRepository;
import com.mesaayuda.repository.AssetRepository;
import com.mesaayuda.repository.SucursalRepository;
import com.mesaayuda.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Activos de TI.
 *
 * <p><b>Quién ve qué</b> ({@link #alcanceVisible(User)}, la misma regla para el
 * listado, la ficha, el historial y la búsqueda global):
 * <ul>
 *   <li>ADMIN y SUPER_ADMIN: todos.</li>
 *   <li>AREA_MANAGER (jefe de área): los de su área, sea por el área del activo
 *       o por el área de la persona que lo tiene, y además los suyos.</li>
 *   <li>Cualquier otro rol: solo los que tiene asignados.</li>
 * </ul>
 *
 * <p><b>Quién gestiona</b>: ADMIN y SUPER_ADMIN, cualquiera; el jefe de área,
 * solo los activos de su área, solo puede asignarlos a personas de su área y
 * no puede mover un activo a otra área. Eliminar es solo de ADMIN/SUPER_ADMIN
 * (lo corta el controlador).
 *
 * <p><b>Estados</b>: ASIGNADO va siempre con persona y DISPONIBLE nunca. Se
 * llega a ASIGNADO asignando, no cambiando el estado a mano. Dar de baja
 * devuelve el activo antes; la reparación conserva a la persona, y al terminar
 * vuelve a ASIGNADO o DISPONIBLE según tenga o no a alguien.
 */
@Service
@Transactional
public class AssetService {

    private static final Logger log = LoggerFactory.getLogger(AssetService.class);

    public static final String DISPONIBLE = "DISPONIBLE";
    public static final String ASIGNADO = "ASIGNADO";
    public static final String EN_REPARACION = "EN_REPARACION";
    public static final String BAJA = "BAJA";

    private static final List<String> VALID_STATUSES = List.of(DISPONIBLE, ASIGNADO, EN_REPARACION, BAJA);

    static final String ROL_AREA_MANAGER = "AREA_MANAGER";

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private AssetHistoryRepository assetHistoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AreaRepository areaRepository;

    @Autowired
    private SucursalRepository sucursalRepository;

    @Autowired
    private AssetMapper assetMapper;

    @Autowired
    private AssetHistoryMapper assetHistoryMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private SystemAuditService systemAuditService;

    @Autowired
    private PresenceService presenceService;

    // ======================================================================
    // Consultas
    // ======================================================================

    @Transactional(readOnly = true)
    public Page<AssetResponse> findAll(String search, String status, String type, UUID areaId, UUID sucursalId, Pageable pageable) {
        User current = userService.getCurrentUser();
        Specification<Asset> filtros = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (areaId != null) {
                predicates.add(cb.equal(root.get("area").get("idArea"), areaId));
            }
            if (sucursalId != null) {
                predicates.add(cb.equal(root.get("sucursal").get("idSucursal"), sucursalId));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(cb.upper(root.get("statusAsset")), status.toUpperCase()));
            }
            if (type != null && !type.isBlank()) {
                predicates.add(cb.equal(cb.upper(root.get("typeAsset")), type.toUpperCase()));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + escaparLike(search.trim().toLowerCase()) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("codeAsset")), pattern),
                        cb.like(cb.lower(root.get("nameAsset")), pattern),
                        cb.like(cb.lower(root.get("brandAsset")), pattern),
                        cb.like(cb.lower(root.get("modelAsset")), pattern),
                        cb.like(cb.lower(root.get("serialNumberAsset")), pattern),
                        cb.like(cb.lower(root.get("typeAsset")), pattern)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        // Sin orden explícito la página cambia de contenido entre peticiones;
        // los más recientes primero y el código como desempate estable.
        Pageable ordenada = pageable.getSort().isSorted() ? pageable
                : org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                        Sort.by(Sort.Order.desc("createdAtAsset"), Sort.Order.asc("codeAsset")));
        return assetRepository.findAll(alcanceVisible(current).and(filtros), ordenada).map(assetMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public AssetResponse findById(UUID id) {
        Asset asset = obtener(id);
        exigirVer(asset);
        return assetMapper.toResponse(asset);
    }

    @Transactional(readOnly = true)
    public List<AssetHistoryResponse> getHistory(UUID assetId) {
        Asset asset = obtener(assetId);
        exigirVer(asset);
        return assetHistoryMapper.toResponse(
                assetHistoryRepository.findByAsset_IdAssetOrderByCreatedAtAssetHistoryDesc(assetId));
    }

    @Transactional(readOnly = true)
    public List<AssetResponse> findByUser(UUID userId) {
        User current = userService.getCurrentUser();
        boolean propio = current.getIdUser().equals(userId);
        if (!propio && !esAdmin(current)) {
            User otro = userRepository.findById(userId).orElse(null);
            boolean deSuArea = esJefeDeArea(current) && otro != null && mismaArea(current, otro.getArea());
            if (!deSuArea) {
                throw new AccessDeniedException("No tiene permisos para ver activos de este usuario");
            }
        }
        return assetMapper.toResponse(assetRepository.findByAssignedUser_IdUserAndDeletedAssetFalse(userId));
    }

    @Transactional(readOnly = true)
    public List<AssetResponse> findByArea(UUID areaId) {
        User current = userService.getCurrentUser();
        if (!esAdmin(current) && !(esJefeDeArea(current) && current.getArea() != null
                && current.getArea().getIdArea().equals(areaId))) {
            throw new AccessDeniedException("No tiene permisos para ver activos de esta área");
        }
        return assetMapper.toResponse(assetRepository.findByArea_IdAreaAndDeletedAssetFalse(areaId));
    }

    @Transactional(readOnly = true)
    public List<AssetResponse> findBySucursal(UUID sucursalId) {
        User current = userService.getCurrentUser();
        if (!esAdmin(current)) {
            throw new AccessDeniedException("Solo administradores pueden ver activos por sucursal");
        }
        return assetMapper.toResponse(assetRepository.findBySucursal_IdSucursalAndDeletedAssetFalse(sucursalId));
    }

    /**
     * Personas a las que el usuario actual puede asignar activos: todas las
     * activas para un administrador, las de su área para un jefe de área.
     * Existe porque /users es solo de administradores y el jefe de área
     * necesita su propia lista para el desplegable "Asignar a".
     */
    @Transactional(readOnly = true)
    public List<AssetAssignableUserResponse> asignables() {
        User current = userService.getCurrentUser();
        if (!esAdmin(current) && !esJefeDeArea(current)) {
            throw new AccessDeniedException("No tiene permisos para asignar activos");
        }
        Specification<User> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.isFalse(root.get("deletedUser")));
            // Un estado nulo no excluye a nadie; solo las cuentas desactivadas.
            p.add(cb.or(cb.isNull(root.get("statusUser")),
                    cb.not(cb.upper(root.get("statusUser")).in("INACTIVE", "INACTIVO"))));
            if (!esAdmin(current)) {
                p.add(current.getArea() == null
                        ? cb.disjunction()
                        : cb.equal(root.get("area").get("idArea"), current.getArea().getIdArea()));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
        List<User> usuarios = userRepository.findAll(spec,
                Sort.by(Sort.Order.asc("displayName").nullsLast(), Sort.Order.asc("corporateEmailUser")));
        Map<UUID, String> estados = presenceService.estadosDe(usuarios.stream().map(User::getIdUser).toList());
        return usuarios.stream()
                .map(u -> new AssetAssignableUserResponse(
                        u.getIdUser(),
                        u.getFullName(),
                        u.getCorporateEmailUser(),
                        u.getPhotoUrl(),
                        u.getArea() != null ? u.getArea().getNameArea() : null,
                        estados.getOrDefault(u.getIdUser(), PresenceService.DESCONECTADO)))
                .toList();
    }

    // ======================================================================
    // Altas y cambios
    // ======================================================================

    public AssetResponse create(AssetRequest request) {
        User current = userService.getCurrentUser();
        String codigo = request.codeAsset().trim();
        if (assetRepository.existsByCodeAsset(codigo)) {
            throw new BadRequestException("Ya existe un activo con el código: " + codigo);
        }
        String pedido = estadoPedido(request.statusAsset());

        Asset asset = new Asset();
        applyRequestToAsset(asset, request);
        asset.setArea(areaPermitida(current, request.areaId()));
        asset.setSucursal(resolverSucursal(request.sucursalId()));
        asset.setStatusAsset(DISPONIBLE);
        asset.setCreatedAtAsset(OffsetDateTime.now());
        // Primero se guarda el activo: el historial lo referencia.
        asset = assetRepository.save(asset);
        registrar(asset, "ALTA", null, null, current, "Alta del activo");

        if (request.assignedUserId() != null) {
            asignarA(asset, usuarioAsignable(current, request.assignedUserId()), current, "Asignación inicial");
        }
        if (EN_REPARACION.equals(pedido) || BAJA.equals(pedido)) {
            cambiarEstado(asset, pedido, current, null);
        }
        asset = assetRepository.save(asset);

        systemAuditService.record("CREATE_ASSET", "Asset", asset.getIdAsset(), current.getCorporateEmailUser(),
                "Activo creado: " + asset.getCodeAsset() + " - " + asset.getNameAsset());
        log.info("Activo creado: {} - {}", asset.getCodeAsset(), asset.getNameAsset());
        return assetMapper.toResponse(asset);
    }

    public AssetResponse update(UUID id, AssetRequest request) {
        User current = userService.getCurrentUser();
        Asset asset = obtener(id);
        exigirGestionar(asset);

        String codigo = request.codeAsset().trim();
        if (!asset.getCodeAsset().equals(codigo) && assetRepository.existsByCodeAsset(codigo)) {
            throw new BadRequestException("Ya existe un activo con el código: " + codigo);
        }
        String pedido = estadoPedido(request.statusAsset());

        applyRequestToAsset(asset, request);
        asset.setArea(areaPermitida(current, request.areaId()));
        asset.setSucursal(resolverSucursal(request.sucursalId()));

        String actual = asset.getStatusAsset();
        // 1) Salir de reparación o de baja antes de tocar la asignación: con el
        //    activo en el taller o dado de baja no se puede asignar.
        if ((EN_REPARACION.equals(actual) || BAJA.equals(actual))
                && (DISPONIBLE.equals(pedido) || ASIGNADO.equals(pedido))) {
            cambiarEstado(asset, DISPONIBLE, current, null);
        }
        // 2) La asignación del formulario.
        UUID antes = asset.getAssignedUser() != null ? asset.getAssignedUser().getIdUser() : null;
        if (request.assignedUserId() != null && !request.assignedUserId().equals(antes)) {
            asignarA(asset, usuarioAsignable(current, request.assignedUserId()), current, null);
        } else if (request.assignedUserId() == null && antes != null) {
            devolver(asset, current, null);
        }
        // 3) Entrar en reparación o baja, si se pidió.
        if ((EN_REPARACION.equals(pedido) || BAJA.equals(pedido)) && !pedido.equals(asset.getStatusAsset())) {
            cambiarEstado(asset, pedido, current, null);
        }

        asset = assetRepository.save(asset);
        systemAuditService.record("UPDATE_ASSET", "Asset", asset.getIdAsset(), current.getCorporateEmailUser(),
                "Activo actualizado: " + asset.getCodeAsset() + " - " + asset.getNameAsset());
        log.info("Activo actualizado: {} - {}", asset.getCodeAsset(), asset.getNameAsset());
        return assetMapper.toResponse(asset);
    }

    public AssetResponse assign(UUID id, UUID userId, String notes) {
        User current = userService.getCurrentUser();
        Asset asset = obtener(id);
        exigirGestionar(asset);
        User destino = usuarioAsignable(current, userId);
        asignarA(asset, destino, current, notes);
        asset = assetRepository.save(asset);

        systemAuditService.record("ASSIGN_ASSET", "Asset", asset.getIdAsset(), current.getCorporateEmailUser(),
                "Activo asignado a " + destino.getCorporateEmailUser());
        return assetMapper.toResponse(asset);
    }

    public AssetResponse unassign(UUID id, String notes) {
        User current = userService.getCurrentUser();
        Asset asset = obtener(id);
        exigirGestionar(asset);
        if (asset.getAssignedUser() == null) {
            throw new BadRequestException("El activo no está asignado a nadie");
        }
        String quien = asset.getAssignedUser().getCorporateEmailUser();
        devolver(asset, current, notes);
        asset = assetRepository.save(asset);

        systemAuditService.record("UNASSIGN_ASSET", "Asset", asset.getIdAsset(), current.getCorporateEmailUser(),
                "Activo devuelto por " + quien);
        return assetMapper.toResponse(asset);
    }

    public AssetResponse changeStatus(UUID id, String status) {
        User current = userService.getCurrentUser();
        Asset asset = obtener(id);
        exigirGestionar(asset);
        String nuevo = estadoPedido(status);
        if (nuevo == null) {
            throw new BadRequestException("Indique el nuevo estado");
        }
        String anterior = asset.getStatusAsset();
        cambiarEstado(asset, nuevo, current, null);
        if (!anterior.equals(asset.getStatusAsset())) {
            asset = assetRepository.save(asset);
            systemAuditService.record("CHANGE_ASSET_STATUS", "Asset", asset.getIdAsset(),
                    current.getCorporateEmailUser(), anterior + " -> " + asset.getStatusAsset());
        }
        return assetMapper.toResponse(asset);
    }

    public void delete(UUID id) {
        User current = userService.getCurrentUser();
        Asset asset = obtener(id);
        exigirGestionar(asset);
        if (!BAJA.equals(asset.getStatusAsset())) {
            cambiarEstado(asset, BAJA, current, "Activo eliminado");
        }
        asset.setDeletedAsset(true);
        assetRepository.save(asset);

        systemAuditService.record("DELETE_ASSET", "Asset", asset.getIdAsset(), current.getCorporateEmailUser(),
                "Activo eliminado (baja lógica): " + asset.getCodeAsset() + " - " + asset.getNameAsset());
        log.info("Activo eliminado (soft delete): {} - {}", asset.getCodeAsset(), asset.getNameAsset());
    }

    // ======================================================================
    // Movimientos (cada uno deja su fila en el historial)
    // ======================================================================

    private void asignarA(Asset asset, User destino, User actor, String notas) {
        if (BAJA.equals(asset.getStatusAsset())) {
            throw new BadRequestException("El activo está dado de baja: reactívelo antes de asignarlo");
        }
        if (EN_REPARACION.equals(asset.getStatusAsset())) {
            throw new BadRequestException("El activo está en reparación: finalice la reparación antes de asignarlo");
        }
        User anterior = asset.getAssignedUser();
        if (anterior != null && anterior.getIdUser().equals(destino.getIdUser())) {
            throw new BadRequestException("El activo ya está asignado a esa persona");
        }
        if (anterior != null) {
            registrar(asset, "REASIGNACION", destino, anterior, actor,
                    texto(notas, "Reasignado de " + anterior.getFullName() + " a " + destino.getFullName()));
        } else {
            registrar(asset, "ASIGNACION", destino, null, actor, texto(notas, "Asignado a " + destino.getFullName()));
        }
        asset.setAssignedUser(destino);
        asset.setStatusAsset(ASIGNADO);
    }

    private void devolver(Asset asset, User actor, String notas) {
        User anterior = asset.getAssignedUser();
        if (anterior == null) {
            return;
        }
        registrar(asset, "DEVOLUCION", anterior, null, actor, texto(notas, "Devuelto por " + anterior.getFullName()));
        asset.setAssignedUser(null);
        if (ASIGNADO.equals(asset.getStatusAsset())) {
            asset.setStatusAsset(DISPONIBLE);
        }
    }

    /**
     * Cambio de estado a mano. ASIGNADO no se pide: se llega asignando. Pedir
     * DISPONIBLE con alguien asignado es una devolución.
     */
    private void cambiarEstado(Asset asset, String nuevo, User actor, String notas) {
        String actual = asset.getStatusAsset();
        if (nuevo.equals(actual)) {
            return;
        }
        User responsable = asset.getAssignedUser();
        switch (nuevo) {
            case ASIGNADO -> {
                if (responsable == null) {
                    throw new BadRequestException("Para marcarlo como asignado, asígnelo a una persona");
                }
                if (!EN_REPARACION.equals(actual)) {
                    throw new BadRequestException("El estado ASIGNADO se obtiene asignando el activo");
                }
                registrar(asset, "FIN_REPARACION", responsable, null, actor, texto(notas, "Reparación finalizada"));
                asset.setStatusAsset(ASIGNADO);
            }
            case DISPONIBLE -> {
                if (EN_REPARACION.equals(actual)) {
                    registrar(asset, "FIN_REPARACION", responsable, null, actor, texto(notas, "Reparación finalizada"));
                    asset.setStatusAsset(responsable != null ? ASIGNADO : DISPONIBLE);
                } else if (BAJA.equals(actual)) {
                    registrar(asset, "ALTA", null, null, actor, texto(notas, "Activo reactivado"));
                    asset.setStatusAsset(DISPONIBLE);
                } else {
                    // ASIGNADO -> DISPONIBLE: es una devolución.
                    devolver(asset, actor, notas);
                }
            }
            case EN_REPARACION -> {
                if (BAJA.equals(actual)) {
                    throw new BadRequestException("Un activo dado de baja no se envía a reparación");
                }
                registrar(asset, "REPARACION", responsable, null, actor, texto(notas, "Enviado a reparación"));
                asset.setStatusAsset(EN_REPARACION);
            }
            case BAJA -> {
                devolver(asset, actor, "Devolución por baja del activo");
                registrar(asset, "BAJA", null, null, actor, texto(notas, "Activo dado de baja"));
                asset.setStatusAsset(BAJA);
            }
            default -> throw new BadRequestException("Estado inválido. Use: DISPONIBLE, ASIGNADO, EN_REPARACION, BAJA");
        }
    }

    private void registrar(Asset asset, String accion, User afectado, User anterior, User actor, String notas) {
        AssetHistory h = new AssetHistory();
        h.setAsset(asset);
        h.setActionType(accion);
        h.setUser(afectado);
        h.setPreviousUser(anterior);
        h.setPerformedBy(actor);
        h.setNotes(notas);
        assetHistoryRepository.save(h);
    }

    // ======================================================================
    // Permisos
    // ======================================================================

    /** Activos que puede ver el usuario. La usan el listado y la búsqueda global. */
    public Specification<Asset> alcanceVisible(User u) {
        return (root, query, cb) -> {
            Predicate vivo = cb.isFalse(root.get("deletedAsset"));
            if (esAdmin(u)) {
                return vivo;
            }
            // LEFT JOIN: un activo sin persona asignada también puede ser del área.
            Join<Asset, User> asignado = root.join("assignedUser", JoinType.LEFT);
            Predicate propio = cb.equal(asignado.get("idUser"), u.getIdUser());
            if (esJefeDeArea(u) && u.getArea() != null) {
                UUID area = u.getArea().getIdArea();
                Join<Asset, Area> areaActivo = root.join("area", JoinType.LEFT);
                Join<User, Area> areaPersona = asignado.join("area", JoinType.LEFT);
                return cb.and(vivo, cb.or(
                        propio,
                        cb.equal(areaActivo.get("idArea"), area),
                        cb.equal(areaPersona.get("idArea"), area)));
            }
            return cb.and(vivo, propio);
        };
    }

    public boolean puedeVer(User u, Asset a) {
        if (esAdmin(u)) return true;
        if (a.getAssignedUser() != null && a.getAssignedUser().getIdUser().equals(u.getIdUser())) return true;
        return esDeSuArea(u, a);
    }

    public boolean puedeGestionar(User u, Asset a) {
        return esAdmin(u) || esDeSuArea(u, a);
    }

    private boolean esDeSuArea(User u, Asset a) {
        if (!esJefeDeArea(u) || u.getArea() == null) return false;
        return mismaArea(u, a.getArea())
                || (a.getAssignedUser() != null && mismaArea(u, a.getAssignedUser().getArea()));
    }

    private void exigirVer(Asset a) {
        if (!puedeVer(userService.getCurrentUser(), a)) {
            throw new AccessDeniedException("No tiene permisos sobre este activo");
        }
    }

    private void exigirGestionar(Asset a) {
        if (!puedeGestionar(userService.getCurrentUser(), a)) {
            throw new AccessDeniedException("Solo puede gestionar activos de su área");
        }
    }

    /** El jefe de área solo asigna a personas de su área. */
    private User usuarioAsignable(User actor, UUID userId) {
        User destino = userRepository.findById(userId)
                .filter(u -> !Boolean.TRUE.equals(u.getDeletedUser()))
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", "id", userId));
        if (!esAdmin(actor) && !mismaArea(actor, destino.getArea())) {
            throw new AccessDeniedException("Solo puede asignar activos a personas de su área");
        }
        return destino;
    }

    /** El jefe de área no puede llevar un activo a otra área; sin área, queda en la suya. */
    private Area areaPermitida(User actor, UUID areaId) {
        if (esAdmin(actor)) {
            return resolverArea(areaId);
        }
        if (actor.getArea() == null) {
            throw new AccessDeniedException("Su usuario no tiene un área asignada");
        }
        if (areaId != null && !areaId.equals(actor.getArea().getIdArea())) {
            throw new AccessDeniedException("Solo puede registrar activos de su área");
        }
        return actor.getArea();
    }

    private static boolean esAdmin(User u) {
        String rol = rolDe(u);
        return "ADMIN".equals(rol) || "SUPER_ADMIN".equals(rol);
    }

    private static boolean esJefeDeArea(User u) {
        return ROL_AREA_MANAGER.equals(rolDe(u));
    }

    private static String rolDe(User u) {
        return u != null && u.getRolUser() != null ? u.getRolUser().getNameRole() : null;
    }

    private static boolean mismaArea(User u, Area area) {
        return u.getArea() != null && area != null && u.getArea().getIdArea().equals(area.getIdArea());
    }

    // ======================================================================
    // Apoyo
    // ======================================================================

    private Asset obtener(UUID id) {
        return assetRepository.findById(id)
                .filter(a -> !Boolean.TRUE.equals(a.getDeletedAsset()))
                .orElseThrow(() -> new ResourceNotFoundException("Activo", "id", id));
    }

    private static String estadoPedido(String estado) {
        if (estado == null || estado.isBlank()) return null;
        String e = estado.trim().toUpperCase();
        if (!VALID_STATUSES.contains(e)) {
            throw new BadRequestException("Estado inválido. Use: DISPONIBLE, ASIGNADO, EN_REPARACION, BAJA");
        }
        return e;
    }

    private static String texto(String notas, String porDefecto) {
        return notas != null && !notas.isBlank() ? notas.trim() : porDefecto;
    }

    private static String recortar(String v) {
        if (v == null) return null;
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }

    /** Mismo escape de LIKE que SearchService: el término es texto literal. */
    private static String escaparLike(String termino) {
        return termino.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private void applyRequestToAsset(Asset asset, AssetRequest request) {
        asset.setCodeAsset(request.codeAsset().trim());
        asset.setNameAsset(request.nameAsset().trim());
        asset.setTypeAsset(request.typeAsset().trim().toUpperCase());
        asset.setBrandAsset(recortar(request.brandAsset()));
        asset.setModelAsset(recortar(request.modelAsset()));
        asset.setSerialNumberAsset(recortar(request.serialNumberAsset()));
        asset.setPurchaseDateAsset(request.purchaseDateAsset());
        asset.setProviderAsset(recortar(request.providerAsset()));
        asset.setCostAsset(request.costAsset());
        asset.setWarrantyUntilAsset(request.warrantyUntilAsset());
        asset.setImageUrlAsset(recortar(request.imageUrlAsset()));
        asset.setDocumentUrlAsset(recortar(request.documentUrlAsset()));
        asset.setNotesAsset(recortar(request.notesAsset()));
    }

    private Area resolverArea(UUID areaId) {
        if (areaId == null) {
            return null;
        }
        return areaRepository.findById(areaId)
                .filter(a -> !Boolean.TRUE.equals(a.getDeletedArea()))
                .orElseThrow(() -> new ResourceNotFoundException("Área", "id", areaId));
    }

    private Sucursal resolverSucursal(UUID sucursalId) {
        if (sucursalId == null) {
            return null;
        }
        return sucursalRepository.findById(sucursalId)
                .filter(s -> !Boolean.TRUE.equals(s.getDeletedSucursal()))
                .orElseThrow(() -> new ResourceNotFoundException("Sucursal", "id", sucursalId));
    }
}
