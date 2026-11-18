package com.mesaayuda.activos;

import com.mesaayuda.entity.Asset;
import com.mesaayuda.entity.User;
import com.mesaayuda.exception.BadRequestException;
import com.mesaayuda.exception.ResourceNotFoundException;
import com.mesaayuda.repository.AreaRepository;
import com.mesaayuda.repository.AssetRepository;
import com.mesaayuda.repository.SucursalRepository;
import com.mesaayuda.repository.UserRepository;
import com.mesaayuda.service.AssetService;
import com.mesaayuda.service.JefaturaService;
import com.mesaayuda.service.NotificationService;
import com.mesaayuda.service.SystemAuditService;
import com.mesaayuda.service.UserService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Actas FOR-POL-TIC-01-10 (Fase 4). Hacer el acta ES el movimiento: entregar
 * asigna los equipos al custodio, devolver los deja en bodega y reasignar los
 * pasa de una persona a otra, todo en una sola operación y con un solo número.
 * <ul>
 *   <li>Varios equipos por acta: el primero es el principal ("Detalle del
 *       bien"); el resto sale en "Accesorios entregados".</li>
 *   <li>Cada fila guarda una foto de los datos del equipo: si el activo cambia
 *       después, el acta no cambia.</li>
 *   <li>El custodio la acepta en el sistema (queda la hora y la IP) y después
 *       se sube el escaneo firmado a mano.</li>
 *   <li>Autorizado por = jefe de Tecnología; Revisado por = la persona de
 *       Tecnología o Desarrollo que se elija.</li>
 * </ul>
 */
@Service
@Transactional
public class ActaActivoService {

    public static final List<String> TIPOS = List.of("ENTREGA", "DEVOLUCION", "REASIGNACION");

    private final ActaActivoRepository actas;
    private final ActaActivoItemRepository items;
    private final AssetRepository activos;
    private final AssetTypeRepository tipos;
    private final AssetService assetService;
    private final UserRepository userRepository;
    private final AreaRepository areaRepository;
    private final SucursalRepository sucursalRepository;
    private final JefaturaService jefaturaService;
    private final NumeracionActivos numeracion;
    private final PermisosActivos permisos;
    private final AssetArchivoService archivos;
    private final ActaPdf actaPdf;
    private final NotificationService notificaciones;
    private final UserService userService;
    private final SystemAuditService auditoria;

    public ActaActivoService(ActaActivoRepository actas, ActaActivoItemRepository items, AssetRepository activos,
                             AssetTypeRepository tipos, AssetService assetService, UserRepository userRepository,
                             AreaRepository areaRepository, SucursalRepository sucursalRepository,
                             JefaturaService jefaturaService, NumeracionActivos numeracion, PermisosActivos permisos,
                             AssetArchivoService archivos, ActaPdf actaPdf, NotificationService notificaciones,
                             UserService userService, SystemAuditService auditoria) {
        this.actas = actas;
        this.items = items;
        this.activos = activos;
        this.tipos = tipos;
        this.assetService = assetService;
        this.userRepository = userRepository;
        this.areaRepository = areaRepository;
        this.sucursalRepository = sucursalRepository;
        this.jefaturaService = jefaturaService;
        this.numeracion = numeracion;
        this.permisos = permisos;
        this.archivos = archivos;
        this.actaPdf = actaPdf;
        this.notificaciones = notificaciones;
        this.userService = userService;
        this.auditoria = auditoria;
    }

    // ================================================================== DTO

    /** activos: el primero es el equipo principal. custodioId no se usa en una devolución. */
    public record ActaRequest(String tipo, UUID custodioId, List<UUID> activos, UUID revisadoPorId, UUID sucursalId,
                              String ciudad, String asunto, String cc, String observaciones) {
    }

    public record Persona(UUID id, String nombre, String cargo, String correo) {
    }

    public record ItemVista(UUID assetId, boolean principal, String codigo, String tipoEquipo, String nombre,
                            String marcaModelo, String serie, String estadoFisico, String accesorios,
                            String configuracion, String novedades) {
    }

    public record ActaVista(UUID id, String numero, String tipo, String estado, LocalDate fecha, String ciudad,
                            String asunto, String cc, Persona custodio, Persona custodioAnterior, Persona entregadoPor,
                            Persona recibidoPor, Persona autorizadoPor, Persona revisadoPor, String sucursal,
                            String observaciones, OffsetDateTime aceptadaAt, boolean firmada, OffsetDateTime firmadaAt,
                            List<ItemVista> items, boolean puedoAceptar, boolean puedoGestionar,
                            OffsetDateTime creada) {
    }

    // ================================================================== crear (= mover)

    public ActaVista crear(ActaRequest r, String ip) {
        User actor = userService.getCurrentUser();
        permisos.exigirGestion(actor);
        String tipo = r == null || r.tipo() == null ? "" : r.tipo().trim().toUpperCase();
        if (!TIPOS.contains(tipo)) {
            throw new BadRequestException("Tipo de acta no válido: ENTREGA, DEVOLUCION o REASIGNACION");
        }
        List<UUID> ids = r.activos() == null ? List.of() : new ArrayList<>(new LinkedHashSet<>(r.activos()));
        if (ids.isEmpty()) {
            throw new BadRequestException("Elija al menos un equipo");
        }
        if (ids.size() > 30) {
            throw new BadRequestException("Como máximo 30 equipos por acta");
        }
        List<Asset> equipos = new ArrayList<>();
        for (UUID id : ids) {
            equipos.add(activos.findById(id).filter(a -> !Boolean.TRUE.equals(a.getDeletedAsset()))
                    .orElseThrow(() -> new ResourceNotFoundException("Activo", "id", id)));
        }
        String notas;
        User custodio;
        User anterior = null;
        User entregado;
        User recibido;
        String numero = numeracion.siguienteActa();
        switch (tipo) {
            case "ENTREGA" -> {
                custodio = persona(r.custodioId(), "Elija a quién se entrega");
                for (Asset a : equipos) {
                    if (a.getAssignedUser() != null) {
                        throw new BadRequestException(a.getCodeAsset() + " ya está asignado a "
                                + a.getAssignedUser().getFullName() + ": use una reasignación o una devolución");
                    }
                }
                notas = "Entrega con acta " + numero;
                for (Asset a : equipos) assetService.assign(a.getIdAsset(), custodio.getIdUser(), notas);
                entregado = actor;
                recibido = custodio;
            }
            case "DEVOLUCION" -> {
                Set<UUID> titulares = new HashSet<>();
                for (Asset a : equipos) {
                    if (a.getAssignedUser() == null) {
                        throw new BadRequestException(a.getCodeAsset() + " ya está en bodega");
                    }
                    titulares.add(a.getAssignedUser().getIdUser());
                }
                if (titulares.size() > 1) {
                    throw new BadRequestException("Una devolución es de una sola persona: los equipos elegidos los tienen varias");
                }
                custodio = equipos.get(0).getAssignedUser();
                notas = "Devolución con acta " + numero;
                for (Asset a : equipos) assetService.unassign(a.getIdAsset(), notas);
                entregado = custodio;
                recibido = actor;
            }
            default -> { // REASIGNACION
                custodio = persona(r.custodioId(), "Elija a quién se reasigna");
                Set<UUID> titulares = new HashSet<>();
                for (Asset a : equipos) {
                    if (a.getAssignedUser() == null) {
                        throw new BadRequestException(a.getCodeAsset() + " está en bodega: use una entrega");
                    }
                    titulares.add(a.getAssignedUser().getIdUser());
                }
                if (titulares.size() > 1) {
                    throw new BadRequestException("Una reasignación pasa los equipos de UNA persona a otra");
                }
                anterior = equipos.get(0).getAssignedUser();
                if (anterior.getIdUser().equals(custodio.getIdUser())) {
                    throw new BadRequestException("Los equipos ya son de esa persona");
                }
                notas = "Reasignación con acta " + numero;
                for (Asset a : equipos) assetService.assign(a.getIdAsset(), custodio.getIdUser(), notas);
                entregado = anterior;
                recibido = custodio;
            }
        }
        User revisado = null;
        if (r.revisadoPorId() != null) {
            revisado = persona(r.revisadoPorId(), "Revisor no válido");
            if (!permisos.gestiona(revisado)) {
                throw new BadRequestException("Revisa el acta alguien de Tecnología o Desarrollo");
            }
        }
        UUID autorizadoId = jefeDeTecnologia();
        exigirNombreYCargo(entregado, recibido, revisado,
                autorizadoId == null ? null : userRepository.findById(autorizadoId).orElse(null));
        AssetConfig c = numeracion.config();
        ActaActivo acta = new ActaActivo();
        acta.setNumero(numero);
        acta.setTipo(tipo);
        acta.setEstado("PENDIENTE");
        acta.setFecha(LocalDate.now(ZoneId.of("America/Guayaquil")));
        acta.setCiudad(r.ciudad() == null || r.ciudad().isBlank() ? c.getCiudad() : recortar(r.ciudad().trim(), 80));
        acta.setAsunto(r.asunto() == null || r.asunto().isBlank() ? asuntoPorDefecto(tipo) : recortar(r.asunto().trim(), 300));
        acta.setCc(r.cc() == null || r.cc().isBlank() ? null : recortar(r.cc().trim(), 300));
        acta.setObservaciones(r.observaciones() == null || r.observaciones().isBlank() ? null : r.observaciones().trim());
        acta.setCustodioId(custodio.getIdUser());
        acta.setCustodioAnteriorId(anterior == null ? null : anterior.getIdUser());
        acta.setEntregadoPorId(entregado.getIdUser());
        acta.setRecibidoPorId(recibido.getIdUser());
        acta.setAutorizadoPorId(autorizadoId);
        acta.setRevisadoPorId(revisado == null ? null : revisado.getIdUser());
        // La sucursal es la del custodio, salvo que se elija otra.
        UUID sucursal = r.sucursalId() != null ? r.sucursalId()
                : (custodio.getSucursal() == null ? null : custodio.getSucursal().getIdSucursal());
        acta.setSucursalId(sucursal);
        acta.setCreadaPor(actor.getIdUser());
        acta = actas.save(acta);

        Map<UUID, String> nombresTipo = new HashMap<>();
        tipos.findAll().forEach(t -> nombresTipo.put(t.getIdAssetType(), t.getNombre()));
        int orden = 0;
        for (Asset a : equipos) {
            ActaActivoItem i = new ActaActivoItem();
            i.setActaId(acta.getIdActa());
            i.setAssetId(a.getIdAsset());
            i.setPrincipal(orden == 0);
            i.setOrden(orden++);
            i.setCodigo(a.getCodeAsset());
            i.setTipoEquipo(a.getAssetTypeId() == null ? a.getTypeAsset() : nombresTipo.getOrDefault(a.getAssetTypeId(), a.getTypeAsset()));
            i.setNombre(a.getNameAsset());
            i.setMarcaModelo(recortar(String.join(" ", Objects.toString(a.getBrandAsset(), ""), Objects.toString(a.getModelAsset(), "")).trim(), 210));
            i.setSerie(a.getSerialNumberAsset());
            i.setEstadoFisico(a.getEstadoFisicoAsset());
            i.setAccesorios(a.getAccesoriosAsset());
            i.setConfiguracion(a.getConfiguracionAsset());
            i.setNovedades(a.getNovedadesAsset());
            items.save(i);
        }
        auditoria.record("ACTA_" + tipo, "ActaActivo", acta.getIdActa(), actor.getCorporateEmailUser(),
                numero + " · " + equipos.size() + " equipo(s) · custodio " + custodio.getCorporateEmailUser());
        // El custodio la acepta en el sistema: aviso en la campana y por correo.
        notificaciones.notifyConEnlace(custodio, "ACTA_ACTIVO", "Acta " + numero + " por aceptar",
                "<p>Tiene un acta de <b>" + texto(tipo).toLowerCase() + "</b> de equipos tecnológicos ("
                        + equipos.size() + " equipo(s)) por revisar y aceptar.</p>", "activos/mis-equipos");
        return vista(acta, actor);
    }

    // ================================================================== aceptar / firmar

    /** El custodio confirma en el sistema que así se le entregó (o que así devolvió). */
    public ActaVista aceptar(UUID id, String ip) {
        User u = userService.getCurrentUser();
        ActaActivo a = acta(id);
        if (!a.getCustodioId().equals(u.getIdUser())) {
            throw new AccessDeniedException("Solo el custodio acepta su acta");
        }
        if (!"PENDIENTE".equals(a.getEstado())) {
            throw new BadRequestException("El acta ya fue aceptada");
        }
        a.setEstado("ACEPTADA");
        a.setAceptadaAt(OffsetDateTime.now());
        a.setAceptadaIp(ip == null ? null : recortar(ip, 64));
        actas.save(a);
        auditoria.record("ACTA_ACEPTADA", "ActaActivo", id, u.getCorporateEmailUser(), a.getNumero() + " desde " + ip);
        if (a.getCreadaPor() != null && !a.getCreadaPor().equals(u.getIdUser())) {
            userRepository.findById(a.getCreadaPor()).ifPresent(c -> notificaciones.notifyConEnlace(c, "ACTA_ACTIVO",
                    "Acta " + a.getNumero() + " aceptada",
                    "<p><b>" + esc(u.getFullName()) + "</b> aceptó el acta <b>" + esc(a.getNumero())
                            + "</b>. Imprímala, recoja las firmas y suba el escaneo.</p>", "activos/actas"));
        }
        return vista(a, u);
    }

    /** Sube el escaneo con las firmas a mano. */
    public ActaVista subirFirmada(UUID id, MultipartFile file) {
        User u = userService.getCurrentUser();
        permisos.exigirGestion(u);
        ActaActivo a = acta(id);
        a.setFirmadaRuta(archivos.guardarActaFirmada(file));
        a.setFirmadaAt(OffsetDateTime.now());
        // El acta firmada a mano vale como aceptación: el custodio ya no tiene que
        // aceptarla en el sistema (aceptar() solo admite PENDIENTE).
        if (a.getAceptadaAt() == null) {
            a.setAceptadaAt(a.getFirmadaAt());
        }
        a.setEstado("FIRMADA");
        actas.save(a);
        auditoria.record("ACTA_FIRMADA", "ActaActivo", id, u.getCorporateEmailUser(), a.getNumero());
        return vista(a, u);
    }

    // ================================================================== consultas

    @Transactional(readOnly = true)
    public List<ActaVista> listar(UUID assetId) {
        User u = userService.getCurrentUser();
        List<ActaActivo> lista;
        if (assetId != null) {
            lista = actas.delActivo(assetId);
        } else if (permisos.veTodo(u)) {
            lista = actas.findAllByOrderByCreatedAtDesc();
        } else {
            lista = actas.findByCustodioIdOrderByCreatedAtDesc(u.getIdUser());
        }
        // Sin "ver todo", solo las propias (custodio actual o anterior).
        return lista.stream()
                .filter(a -> permisos.veTodo(u) || u.getIdUser().equals(a.getCustodioId())
                        || u.getIdUser().equals(a.getCustodioAnteriorId()))
                .map(a -> vista(a, u)).toList();
    }

    @Transactional(readOnly = true)
    public ActaVista detalle(UUID id) {
        User u = userService.getCurrentUser();
        ActaActivo a = legible(id, u);
        return vista(a, u);
    }

    @Transactional(readOnly = true)
    public byte[] pdf(UUID id) {
        User u = userService.getCurrentUser();
        ActaActivo a = legible(id, u);
        ActaVista v = vista(a, u);
        // En la entrega van las fotos del bien principal.
        List<byte[]> fotos = "ENTREGA".equals(v.tipo()) && !v.items().isEmpty()
                ? archivos.fotosDe(v.items().get(0).assetId()) : List.of();
        return actaPdf.generar(v, numeracion.config(), area(a.getCustodioId()), cedula(a.getCustodioId()), fotos);
    }

    @Transactional(readOnly = true)
    public AssetArchivoService.Descarga firmada(UUID id) {
        User u = userService.getCurrentUser();
        ActaActivo a = legible(id, u);
        if (a.getFirmadaRuta() == null) {
            throw new ResourceNotFoundException("Acta firmada", "acta", id);
        }
        return new AssetArchivoService.Descarga(a.getNumero() + "-firmada" + a.getFirmadaRuta().substring(a.getFirmadaRuta().lastIndexOf('.')),
                AssetArchivoService.mimeDe(a.getFirmadaRuta()), archivos.leer(a.getFirmadaRuta()));
    }

    /** Quién puede revisar actas (personal de Tecnología y Desarrollo), para el desplegable. */
    @Transactional(readOnly = true)
    public List<Persona> revisores() {
        permisos.exigirGestion(userService.getCurrentUser());
        return userRepository.findByStatusUserAndDeletedUserFalse("ACTIVE").stream()
                .filter(permisos::gestiona)
                .map(ActaActivoService::persona)
                .sorted((x, y) -> x.nombre().compareToIgnoreCase(y.nombre()))
                .toList();
    }

    // ================================================================== apoyo

    private ActaActivo acta(UUID id) {
        return actas.findById(id).orElseThrow(() -> new ResourceNotFoundException("Acta", "id", id));
    }

    private ActaActivo legible(UUID id, User u) {
        ActaActivo a = acta(id);
        if (!permisos.veTodo(u) && !u.getIdUser().equals(a.getCustodioId()) && !u.getIdUser().equals(a.getCustodioAnteriorId())) {
            throw new AccessDeniedException("Esta acta es de otra persona");
        }
        return a;
    }

    private User persona(UUID id, String siFalta) {
        if (id == null) {
            throw new BadRequestException(siFalta);
        }
        return userRepository.findById(id).filter(x -> !Boolean.TRUE.equals(x.getDeletedUser()))
                .orElseThrow(() -> new BadRequestException(siFalta));
    }

    /** Autoriza el jefe del área de Tecnología (el primero activo). */
    private UUID jefeDeTecnologia() {
        return areaRepository.tecnologia()
                .flatMap(area -> jefaturaService.jefesDe(area.getIdArea()).stream().findFirst())
                .map(User::getIdUser).orElse(null);
    }

    private String area(UUID userId) {
        return userRepository.findById(userId).map(x -> x.getArea() == null ? "" : x.getArea().getNameArea()).orElse("");
    }

    private String cedula(UUID userId) {
        return userRepository.findById(userId).map(x -> Objects.toString(x.getIdentificationUser(), "")).orElse("");
    }

    private ActaVista vista(ActaActivo a, User u) {
        Map<UUID, User> gente = new HashMap<>();
        Set<UUID> ids = new HashSet<>();
        for (UUID x : new UUID[]{a.getCustodioId(), a.getCustodioAnteriorId(), a.getEntregadoPorId(),
                a.getRecibidoPorId(), a.getAutorizadoPorId(), a.getRevisadoPorId()}) {
            if (x != null) ids.add(x);
        }
        userRepository.findAllById(ids).forEach(x -> gente.put(x.getIdUser(), x));
        String sucursal = a.getSucursalId() == null ? null
                : sucursalRepository.findById(a.getSucursalId()).map(s -> s.getNameSucursal()).orElse(null);
        List<ItemVista> its = items.findByActaIdOrderByOrdenAsc(a.getIdActa()).stream()
                .map(i -> new ItemVista(i.getAssetId(), Boolean.TRUE.equals(i.getPrincipal()), i.getCodigo(), i.getTipoEquipo(),
                        i.getNombre(), i.getMarcaModelo(), i.getSerie(), i.getEstadoFisico(), i.getAccesorios(),
                        i.getConfiguracion(), i.getNovedades()))
                .toList();
        return new ActaVista(a.getIdActa(), a.getNumero(), a.getTipo(), a.getEstado(), a.getFecha(), a.getCiudad(),
                a.getAsunto(), a.getCc(), p(gente, a.getCustodioId()), p(gente, a.getCustodioAnteriorId()),
                p(gente, a.getEntregadoPorId()), p(gente, a.getRecibidoPorId()), p(gente, a.getAutorizadoPorId()),
                p(gente, a.getRevisadoPorId()), sucursal, a.getObservaciones(), a.getAceptadaAt(),
                a.getFirmadaRuta() != null, a.getFirmadaAt(), its,
                "PENDIENTE".equals(a.getEstado()) && u.getIdUser().equals(a.getCustodioId()),
                permisos.gestiona(u), a.getCreatedAt());
    }

    private static Persona p(Map<UUID, User> gente, UUID id) {
        return id == null || !gente.containsKey(id) ? null : persona(gente.get(id));
    }

    /**
     * El acta lleva nombre y cargo de cada firmante (formulario FOR-POL-TIC-01-10).
     * Antes, sin cargo se imprimía uno inventado ("JEFE DE TIC") y sin nombre el
     * correo. Ahora se pide completarlos en Usuarios antes de generarla; como el
     * método es transaccional, el movimiento de los equipos se revierte.
     */
    private static void exigirNombreYCargo(User... personas) {
        List<String> faltan = faltantes(personas);
        if (!faltan.isEmpty()) {
            throw new BadRequestException("Para el acta falta completar en Usuarios: " + String.join("; ", faltan));
        }
    }

    /** "correo (nombre y cargo)" de cada firmante al que le falta algo; vacío si están todos. */
    private static List<String> faltantes(User... personas) {
        List<String> faltan = new ArrayList<>();
        Set<UUID> vistos = new HashSet<>();
        for (User u : personas) {
            if (u == null || !vistos.add(u.getIdUser())) continue;
            List<String> f = new ArrayList<>();
            if (u.getCargoUser() == null || u.getCargoUser().isBlank()) f.add("cargo");
            if (!f.isEmpty()) faltan.add(u.getCorporateEmailUser() + " (" + String.join(" y ", f) + ")");
        }
        return faltan;
    }

    /**
     * Antes de generar: quiénes firmarían el acta y no tienen nombre o cargo. El
     * modal lo pregunta al elegir a las personas, así avisa antes de pulsar el
     * botón en lugar de fallar al final.
     */
    @Transactional(readOnly = true)
    public List<String> faltantesPara(ActaRequest r) {
        User actor = userService.getCurrentUser();
        permisos.exigirGestion(actor);
        if (r == null) return List.of();
        String tipo = r.tipo() == null ? "" : r.tipo().trim().toUpperCase();
        User titular = null;
        if (r.activos() != null && !r.activos().isEmpty()) {
            titular = activos.findById(r.activos().get(0)).map(Asset::getAssignedUser).orElse(null);
        }
        User custodio = r.custodioId() == null ? null : userRepository.findById(r.custodioId()).orElse(null);
        User revisado = r.revisadoPorId() == null ? null : userRepository.findById(r.revisadoPorId()).orElse(null);
        UUID autorizadoId = jefeDeTecnologia();
        User autorizado = autorizadoId == null ? null : userRepository.findById(autorizadoId).orElse(null);
        return switch (tipo) {
            case "DEVOLUCION" -> faltantes(titular, actor, revisado, autorizado);
            case "REASIGNACION" -> faltantes(titular, custodio, revisado, autorizado);
            default -> faltantes(actor, custodio, revisado, autorizado);
        };
    }

    private static Persona persona(User x) {
        String nombre = x.getDisplayName() == null || x.getDisplayName().isBlank()
                ? com.mesaayuda.service.UserService.nombreDesdeCorreo(x.getCorporateEmailUser()) : x.getDisplayName();
        return new Persona(x.getIdUser(), nombre, x.getCargoUser(), x.getCorporateEmailUser());
    }

    static String texto(String tipo) {
        return switch (tipo) {
            case "ENTREGA" -> "Entrega";
            case "DEVOLUCION" -> "Devolución";
            default -> "Reasignación";
        };
    }

    private static String asuntoPorDefecto(String tipo) {
        return texto(tipo) + " de equipos tecnológicos";
    }

    private static String recortar(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
