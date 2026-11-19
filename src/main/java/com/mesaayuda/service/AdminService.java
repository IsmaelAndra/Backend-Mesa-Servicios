package com.mesaayuda.service;

import com.mesaayuda.entity.*;
import com.mesaayuda.exception.BadRequestException;
import com.mesaayuda.exception.ResourceNotFoundException;
import com.mesaayuda.repository.*;
import com.mesaayuda.dto.AuditEventResponse;
import com.mesaayuda.dto.AiConfigRequest;
import com.mesaayuda.dto.AiConfigResponse;
import org.springframework.beans.factory.annotation.Autowired;
import com.mesaayuda.config.UploadZones;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class AdminService {

    @Autowired private RoleRepository roleRepository;
    @Autowired private AreaRepository areaRepository;
    @Autowired private SucursalRepository sucursalRepository;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private StatusRepository statusRepository;
    @Autowired private PriorityRepository priorityRepository;
    @Autowired private SupportTeamRepository teamRepository;
    @Autowired private TeamUserRepository teamUserRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private SlaPolicyRepository slaPolicyRepository;
    @Autowired private SlaRuleRepository slaRuleRepository;
    @Autowired private SlaEscalationRepository escalationRepository;
    @Autowired private NotificationTemplateRepository templateRepository;
    @Autowired private NotificationTemplateImageRepository templateImageRepository;
    @Autowired private TicketTypeRepository ticketTypeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private BrandingConfigRepository brandingConfigRepository;
    @Autowired(required = false) private org.springframework.jdbc.core.JdbcTemplate jdbcTemplateIa;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private com.mesaayuda.repository.AiConfigRepository aiConfigRepository;
    @Autowired private com.mesaayuda.security.UrlSafetyValidator urlSafetyValidator;
    /** Cifra las credenciales de proveedores externos: Graph y la IA. */
    @Autowired private SecretCryptoService secretCryptoService;

    @Value("${app.uploads.dir}")
    private String uploadDir;

    /** Prefijo de la zona publica. El unico admitido como ruta local de marca. */
    private static final String PUBLIC_UPLOAD_PREFIX = "/uploads/" + UploadZones.PUBLICO + "/";
    /** Tope de la imagen de plantilla almacenada en la base: 2 MB. */
    private static final long TEMPLATE_IMAGE_MAX_BYTES = 2L * 1024 * 1024;

    @Transactional(readOnly = true)
    public Map<String, Object> getCatalog() {
        Map<String, Object> catalog = new HashMap<>();
        catalog.put("roles", roleRepository.findAll().stream()
                .filter(r -> !Boolean.TRUE.equals(r.getDeletedRole()))
                .map(Role::getNameRole).toList());
        catalog.put("areas", areaRepository.findByDeletedAreaFalseOrderByPriorityLevelDesc().stream()
                .map(a -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", a.getIdArea());
                    m.put("name", a.getNameArea());
                    m.put("icon", orEmpty(a.getIconArea()));
                    m.put("color", orEmpty(a.getColorArea()));
                    m.put("priorityLevel", a.getPriorityLevel());
                    m.put("status", a.getStatusArea());
                    return m;
                }).toList());
        // El catalogo es publico y lo consumen todos los selectores de la
        // aplicacion, asi que la sucursal tambien va aqui en vez de obligar a un
        // endpoint por cada formulario.
        catalog.put("sucursales", sucursalRepository
                .findByDeletedSucursalFalseAndStatusSucursalTrueOrderByNameSucursalAsc().stream()
                .map(s -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", s.getIdSucursal());
                    m.put("code", orEmpty(s.getCodeSucursal()));
                    m.put("name", orEmpty(s.getNameSucursal()));
                    m.put("icon", orEmpty(s.getIconSucursal()));
                    m.put("color", orEmpty(s.getColorSucursal()));
                    m.put("status", s.getStatusSucursal());
                    return m;
                }).toList());
        catalog.put("statuses", statusRepository.findAll().stream().map(s -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", s.getIdStatus());
            m.put("name", s.getNameStatus());
            m.put("icon", orEmpty(s.getIconStatus()));
            m.put("color", orEmpty(s.getColorStatus()));
            return m;
        }).toList());
        catalog.put("priorities", priorityRepository.findAllActive().stream().map(p -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", p.getIdPriority());
            m.put("name", p.getNamePriority());
            m.put("level", p.getLevelPriority());
            m.put("icon", orEmpty(p.getIconPriority()));
            m.put("color", orEmpty(p.getColorPriority()));
            return m;
        }).toList());
        catalog.put("teams", teamRepository.findByActiveTeamTrue().stream().map(t -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", t.getIdTeam());
            m.put("name", t.getNameTeam());
            m.put("method", t.getAssignmentMethod());
            m.put("areaId", t.getAreaId());
            m.put("icon", orEmpty(t.getIconTeam()));
            m.put("color", orEmpty(t.getColorTeam()));
            return m;
        }).toList());
        catalog.put("categories", categoryRepository.findByDeletedCategoryFalse().stream().map(c -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", c.getIdCategory());
            m.put("name", c.getNameCategory());
            // La usa la IA para elegir bien la categoría al crear un ticket.
            m.put("description", orEmpty(c.getDescriptionCategory()));
            m.put("icon", orEmpty(c.getIconCategory()));
            m.put("color", orEmpty(c.getColorCategory()));
            m.put("teamId", c.getTeamCategory() != null ? c.getTeamCategory().getIdTeam() : null);
            m.put("areaId", c.getAreaId());
            return m;
        }).toList());
        catalog.put("ticketTypes", ticketTypeRepository.findByDeletedTicketTypeFalseOrderByNameTicketTypeAsc().stream()
                .map(t -> Map.<String, Object>of(
                        "id", t.getIdTicketType(),
                        "name", t.getNameTicketType(),
                        "icon", orEmpty(t.getIconTicketType()),
                        "color", orEmpty(t.getColorTicketType()),
                        "description", orEmpty(t.getDescriptionTicketType()))).toList());
        catalog.put("slaPolicies", slaPolicyRepository.findByActiveSlaPolicyTrue().stream()
                .map(s -> {
                    // El icono y el color se editan en la pantalla de políticas, así que
                    // el catálogo los tiene que devolver: era la única entidad del
                    // catálogo que los perdía, y por eso todas las políticas salían
                    // con el mismo icono de escudo y sin color propio. Se usa HashMap
                    // en vez de Map.of porque los tiempos pueden venir a null y
                    // Map.of no admite valores nulos.
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", s.getIdSlaPolicy());
                    m.put("name", s.getNameSlaPolicy());
                    m.put("description", orEmpty(s.getDescriptionSlaPolicy()));
                    m.put("response", s.getResponseTimeMinutes());
                    m.put("resolution", s.getResolutionTimeMinutes());
                    m.put("icon", orEmpty(s.getIconSlaPolicy()));
                    m.put("color", orEmpty(s.getColorSlaPolicy()));
                    return m;
                }).toList());
        return catalog;
    }

    private String orEmpty(String value) {
        return value == null ? "" : value;
    }

    // ============================ TIPOS DE TICKET ============================

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTicketTypes() {
        return ticketTypeRepository.findByDeletedTicketTypeFalseOrderByNameTicketTypeAsc().stream()
                .map(t -> Map.<String, Object>of(
                        "id", t.getIdTicketType(),
                        "name", t.getNameTicketType(),
                        "description", t.getDescriptionTicketType() == null ? "" : t.getDescriptionTicketType(),
                        "icon", t.getIconTicketType() == null ? "" : t.getIconTicketType(),
                        "color", t.getColorTicketType() == null ? "" : t.getColorTicketType()))
                .toList();
    }

    @Transactional
    public Map<String, Object> saveTicketType(String name, String description, String icon, String color) {
        String nombre = nombreObligatorio(name, "del tipo", 100).toUpperCase();
        exigirTipoLibre(nombre, null);
        TicketType t = new TicketType();
        t.setNameTicketType(nombre);
        t.setDescriptionTicketType(description == null ? null : description.trim());
        t.setIconTicketType(icon == null || icon.isBlank() ? null : icon.trim());
        t.setColorTicketType(color == null || color.isBlank() ? null : color.trim());
        t.setDeletedTicketType(false);
        t = ticketTypeRepository.save(t);
        return Map.of("id", t.getIdTicketType(), "name", t.getNameTicketType(),
                "icon", t.getIconTicketType() == null ? "" : t.getIconTicketType(),
                "color", t.getColorTicketType() == null ? "" : t.getColorTicketType());
    }

    @Transactional
    public Map<String, Object> updateTicketType(UUID id, String name, String description, String icon, String color) {
        TicketType t = ticketTypeRepository.findById(id)
                .filter(x -> !x.getDeletedTicketType())
                .orElseThrow(() -> new ResourceNotFoundException("Tipo de ticket", "id", id));
        if (name != null) {
            String nombre = nombreObligatorio(name, "del tipo", 100).toUpperCase();
            exigirTipoLibre(nombre, t.getIdTicketType());
            t.setNameTicketType(nombre);
        }
        if (description != null) {
            t.setDescriptionTicketType(description.trim());
        }
        if (icon != null) {
            t.setIconTicketType(icon.isBlank() ? null : icon.trim());
        }
        if (color != null) {
            t.setColorTicketType(color.isBlank() ? null : color.trim());
        }
        t = ticketTypeRepository.save(t);
        return Map.of("id", t.getIdTicketType(), "name", t.getNameTicketType(),
                "icon", t.getIconTicketType() == null ? "" : t.getIconTicketType(),
                "color", t.getColorTicketType() == null ? "" : t.getColorTicketType());
    }

    @Transactional
    public void deleteTicketType(UUID id) {
        TicketType t = ticketTypeRepository.findById(id)
                .filter(x -> !x.getDeletedTicketType())
                .orElseThrow(() -> new ResourceNotFoundException("Tipo de ticket", "id", id));
        t.setDeletedTicketType(true);
        t.setNameTicketType(nombreDeEliminado(t.getNameTicketType(), 100));
        ticketTypeRepository.save(t);
    }

    /** Se guarda en mayúsculas: "Incidente" y "INCIDENTE" chocaban en la base (500). */
    private void exigirTipoLibre(String nombre, UUID propio) {
        boolean repetido = ticketTypeRepository.findAll().stream()
                .anyMatch(x -> !Boolean.TRUE.equals(x.getDeletedTicketType())
                        && !x.getIdTicketType().equals(propio)
                        && mismoNombre(x.getNameTicketType(), nombre));
        if (repetido) {
            throw new BadRequestException("Ya existe un tipo llamado \"" + nombre + "\"");
        }
    }

    // ============================ BRANDING ============================

    @Transactional(readOnly = true)
    public BrandingConfig getBranding() {
        return brandingConfigRepository.findTopByOrderByIdBrandingAsc()
                .orElseGet(() -> {
                    BrandingConfig bc = new BrandingConfig();
                    bc.setPrimaryColor("#062e86");
                    bc.setSecondaryColor("#ec9811");
                    return bc;
                });
    }

    /**
     * Las imágenes de marca se pintan en todas las pantallas, y una de ellas
     * (el logo del reporte) la descarga además el propio servidor. Una URL
     * guardada sin comprobar permitiría apuntar a la red interna o a los
     * metadatos de la nube. Se aceptan las rutas locales de /uploads, que es
     * como quedan los archivos que sube la propia aplicación.
     */
    private void comprobarUrlsDeMarca(BrandingConfig config) {
        comprobarUrl(config.getBackgroundUrl(), "El fondo");
        comprobarUrl(config.getLogoUrl(), "El logo");
        comprobarUrl(config.getLogoLoginUrl(), "El logo de acceso");
        comprobarUrl(config.getSystemIconUrl(), "El icono del sistema");
        comprobarUrl(config.getReportLogoUrl(), "El logo de reportes");
        comprobarUrl(config.getProfileCoverUrl(), "La portada del perfil");
        comprobarUrl(config.getChatBackgroundUrl(), "El fondo del chat");
        comprobarUrl(config.getChatBackgroundLightUrl(), "El fondo claro del chat");
        comprobarUrl(config.getChatBackgroundDarkUrl(), "El fondo oscuro del chat");
        comprobarUrl(config.getMaintenanceLogoUrl(), "El logo de mantenimiento");
        comprobarUrl(config.getLoadingImageUrl(), "La imagen de la pantalla de carga");
    }

    /**
     * Deja las URLs de marca en su forma relativa antes de validar y guardar.
     * Se hace sobre el objeto que llega del formulario, para que lo que queda
     * en la base no dependa del host desde el que se administró.
     */
    private void normalizarUrlsDeMarca(BrandingConfig config) {
        config.setBackgroundUrl(normalizarUrlDeMarca(config.getBackgroundUrl()));
        config.setLogoUrl(normalizarUrlDeMarca(config.getLogoUrl()));
        config.setLogoLoginUrl(normalizarUrlDeMarca(config.getLogoLoginUrl()));
        config.setSystemIconUrl(normalizarUrlDeMarca(config.getSystemIconUrl()));
        config.setReportLogoUrl(normalizarUrlDeMarca(config.getReportLogoUrl()));
        config.setProfileCoverUrl(normalizarUrlDeMarca(config.getProfileCoverUrl()));
        config.setChatBackgroundUrl(normalizarUrlDeMarca(config.getChatBackgroundUrl()));
        config.setChatBackgroundLightUrl(normalizarUrlDeMarca(config.getChatBackgroundLightUrl()));
        config.setChatBackgroundDarkUrl(normalizarUrlDeMarca(config.getChatBackgroundDarkUrl()));
        config.setMaintenanceLogoUrl(normalizarUrlDeMarca(config.getMaintenanceLogoUrl()));
        config.setLoadingImageUrl(normalizarUrlDeMarca(config.getLoadingImageUrl()));
    }

    private void comprobarUrl(String valor, String nombre) {
        if (valor == null || valor.isBlank()) {
            return;
        }
        String limpio = normalizarUrlDeMarca(valor.trim());
        if (!limpio.toLowerCase().startsWith("http")) {
            // Solo se admiten rutas de la zona publica. Aceptar "/uploads/..."
            // a secas permitiria apuntar a un adjunto de ticket, que no es un
            // recurso de marca y ademas exige permiso de lectura.
            if (!limpio.startsWith(PUBLIC_UPLOAD_PREFIX)) {
                throw new BadRequestException(nombre + " debe ser una ruta "
                        + PUBLIC_UPLOAD_PREFIX + "... o una dirección http(s).");
            }
            return;
        }
        urlSafetyValidator.exigirUrlPublica(limpio, nombre);
    }

    /**
     * Las respuestas de /admin/branding devuelven las imágenes de marca como
     * URLs absolutas (http://host/api/uploads/publico/fondo.jpg) para que el
     * navegador las pinte sin depender del contexto. El formulario, en cambio,
     * manda el mismo objeto de vuelta al guardar: si ese valor absoluto se
     * guardara tal cual, al desplegar en otro dominio las imágenes apuntarían
     * al host viejo, y de paso el guardado se rechazaría siempre, porque la
     * comprobación de red interna no admite localhost.
     *
     * Por eso, cuando la URL apunta a un archivo nuestro de la zona pública,
     * se devuelve a su forma relativa antes de validar. Solo se toca lo que
     * cuelga de /uploads/publico/, así que el resultado nunca es un destino
     * externo: como mucho, un archivo del propio servidor.
     */
    private String normalizarUrlDeMarca(String valor) {
        if (valor == null || valor.isBlank()) {
            return valor;
        }
        if (!valor.trim().toLowerCase().startsWith("http")) {
            return valor.trim();
        }
        int indice = valor.indexOf(PUBLIC_UPLOAD_PREFIX);
        if (indice < 0) {
            return valor;
        }
        // Puede haber un query string detras (versionado de cache).
        String ruta = valor.substring(indice);
        int corte = ruta.length();
        for (char separador : new char[] { '?', '#' }) {
            int pos = ruta.indexOf(separador);
            if (pos >= 0 && pos < corte) {
                corte = pos;
            }
        }
        return ruta.substring(0, corte);
    }

    public BrandingConfig updateBranding(BrandingConfig config) {
        normalizarUrlsDeMarca(config);
        comprobarUrlsDeMarca(config);
        BrandingConfig current = getBranding();
        if (config.getPrimaryColor() != null) current.setPrimaryColor(config.getPrimaryColor());
        if (config.getSecondaryColor() != null) current.setSecondaryColor(config.getSecondaryColor());
        if (config.getBackgroundUrl() != null && !config.getBackgroundUrl().isBlank()) {
            current.setBackgroundUrl(config.getBackgroundUrl());
        }
        if (config.getLogoLoginUrl() != null && !config.getLogoLoginUrl().isBlank()) {
            current.setLogoLoginUrl(config.getLogoLoginUrl());
        }
        if (config.getSystemIconUrl() != null && !config.getSystemIconUrl().isBlank()) {
            current.setSystemIconUrl(config.getSystemIconUrl());
        }
        if (config.getReportLogoUrl() != null && !config.getReportLogoUrl().isBlank()) {
            current.setReportLogoUrl(config.getReportLogoUrl());
        }
        if (config.getProfileCoverUrl() != null && !config.getProfileCoverUrl().isBlank()) {
            current.setProfileCoverUrl(config.getProfileCoverUrl());
        }
        if (config.getChatBackgroundUrl() != null && !config.getChatBackgroundUrl().isBlank()) {
            current.setChatBackgroundUrl(config.getChatBackgroundUrl());
        }
        if (config.getChatBackgroundLightUrl() != null && !config.getChatBackgroundLightUrl().isBlank()) {
            current.setChatBackgroundLightUrl(config.getChatBackgroundLightUrl());
        }
        if (config.getChatBackgroundDarkUrl() != null && !config.getChatBackgroundDarkUrl().isBlank()) {
            current.setChatBackgroundDarkUrl(config.getChatBackgroundDarkUrl());
        }
        // Modo mantenimiento. El booleano si se copia siempre (para poder
        // apagarlo), los textos solo si vienen con contenido.
        if (config.getMaintenanceMode() != null) {
            current.setMaintenanceMode(config.getMaintenanceMode());
        }
        if (config.getMaintenanceTitle() != null) {
            current.setMaintenanceTitle(config.getMaintenanceTitle().isBlank() ? null : config.getMaintenanceTitle());
        }
        if (config.getMaintenanceMessage() != null) {
            current.setMaintenanceMessage(config.getMaintenanceMessage().isBlank() ? null : config.getMaintenanceMessage());
        }
        if (config.getMaintenanceEta() != null) {
            current.setMaintenanceEta(config.getMaintenanceEta().isBlank() ? null : config.getMaintenanceEta());
        }
        if (config.getMaintenanceLogoUrl() != null) {
            current.setMaintenanceLogoUrl(config.getMaintenanceLogoUrl().isBlank() ? null : config.getMaintenanceLogoUrl());
        }
        if (config.getMaintenanceIcon() != null) {
            current.setMaintenanceIcon(config.getMaintenanceIcon().isBlank() ? null : config.getMaintenanceIcon());
        }
        if (config.getMaintenanceIconColor() != null) {
            current.setMaintenanceIconColor(config.getMaintenanceIconColor().isBlank() ? null : config.getMaintenanceIconColor());
        }
        // Pantalla de carga. A diferencia del resto de imagenes de marca, aqui
        // si se admite vaciar la imagen o el icono: asi se vuelve al icono del
        // sistema sin tocar la base de datos a mano.
        current.setLoadingMode(normalizarEnum(config.getLoadingMode(),
                Set.of("AUTO", "IMAGE", "ICON"), "AUTO"));
        current.setLoadingAnimation(normalizarEnum(config.getLoadingAnimation(),
                Set.of("SPINNER", "RING", "DOTS", "BARS", "BALL", "INFINITY", "PULSE", "NONE"), "SPINNER"));
        current.setLoadingLogoEffect(normalizarEnum(config.getLoadingLogoEffect(),
                Set.of("NONE", "FADE", "FADE_IN", "SCALE_PULSE", "ZOOM", "FLOAT",
                        "BOUNCE", "GLOW", "SHIMMER", "SWING", "SPIN"), "NONE"));
        current.setLoadingTheme(normalizarEnum(config.getLoadingTheme(),
                Set.of("LIGHT", "DARK"), "LIGHT"));
        if (config.getLoadingImageUrl() != null) {
            current.setLoadingImageUrl(config.getLoadingImageUrl().isBlank() ? null : config.getLoadingImageUrl());
        }
        if (config.getLoadingIcon() != null) {
            current.setLoadingIcon(config.getLoadingIcon().isBlank() ? null : config.getLoadingIcon());
        }
        if (config.getLoadingTitle() != null) {
            current.setLoadingTitle(config.getLoadingTitle().isBlank() ? null : config.getLoadingTitle());
        }
        if (config.getLoadingMessage() != null) {
            current.setLoadingMessage(config.getLoadingMessage().isBlank() ? null : config.getLoadingMessage());
        }
        if (config.getLoadingBackgroundColor() != null && !config.getLoadingBackgroundColor().isBlank()) {
            current.setLoadingBackgroundColor(config.getLoadingBackgroundColor());
        }
        if (config.getLoadingTextColor() != null && !config.getLoadingTextColor().isBlank()) {
            current.setLoadingTextColor(config.getLoadingTextColor());
        }
        if (config.getLoadingProgress() != null) {
            current.setLoadingProgress(config.getLoadingProgress());
        }
        if (config.getLoadingSoloMarca() != null) {
            current.setLoadingSoloMarca(config.getLoadingSoloMarca());
        }
        if (config.getLoadingImageSize() != null) {
            current.setLoadingImageSize(Math.min(Math.max(config.getLoadingImageSize(), 48), 256));
        }
        if (config.getLoadingMinDurationMs() != null) {
            // 0 .. 10 s: por debajo de 0 es un error de dedo y por encima el
            // splash se queda congelado mirando a la pantalla.
            current.setLoadingMinDurationMs(Math.min(Math.max(config.getLoadingMinDurationMs(), 0), 10000));
        }
        return brandingConfigRepository.save(current);
    }

    /**
     * Los modos del splash llegan desde el formulario. Se aceptan sin
     * distinguir mayusculas y cualquier valor fuera de la lista permitida
     * cae en el predeterminado, en vez de guardar un estado que la pantalla
     * no sabria pintar.
     */
    private String normalizarEnum(String valor, Set<String> permitidos, String porDefecto) {
        if (valor == null || valor.isBlank()) {
            return porDefecto;
        }
        String limpio = valor.trim().toUpperCase();
        return permitidos.contains(limpio) ? limpio : porDefecto;
    }

    /**
 * Extensión que corresponde a cada tipo de imagen admitido.
 *
 * <p>Se deriva del contenido y nunca del nombre que envía el cliente: así el
 * archivo guardado lleva siempre la extensión que le corresponde y el navegador
 * lo interpreta como lo que es.
 */
private static final Map<String, String> EXTENSION_POR_TIPO = Map.of(
        "image/png", ".png",
        "image/jpeg", ".jpg",
        "image/gif", ".gif",
        "image/webp", ".webp");

/**
 * Tope de una imagen de marca. Las de una institución son pequenas y no
 * necesitan más; sin un limite, repetir la subida exhausta el disco con copias
 * que nunca se limpian.
 */
private static final long IMAGE_MAX_BYTES = 5L * 1024 * 1024;

/**
 * Guarda una imagen en la zona pública con las mismas comprobaciones que la
 * marca (firma del archivo, sin SVG, tope de tamaño). La usan también los
 * temas de temporada para su fondo y su logo.
 */
public Map<String, String> guardarImagenPublica(MultipartFile file, String prefix) {
    return saveImage(file, prefix);
}

private Map<String, String> saveImage(MultipartFile file, String prefix) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("El archivo está vacío");
        }
        if (file.getSize() > IMAGE_MAX_BYTES) {
            throw new BadRequestException("La imagen supera el máximo de "
                    + (IMAGE_MAX_BYTES / 1024 / 1024) + " MB");
        }

        byte[] contenido;
        try {
            contenido = file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("No se pudo leer el archivo enviado");
        }

        // El tipo se deduce de la firma del archivo, no de lo que declare el
        // cliente. Antes se aceptaba cualquier cosa y la extensión salía del
        // nombre: un archivo llamado logo.html quedaba guardado como HTML y uno
        // llamado logo.svg como SVG. Y eso importa porque la zona pública se
        // sirve SIN SESIÓN, de modo que un SVG con <script> depositado ahí se
        // ejecuta en el origen de la API. No era hipotético: la carpeta ya
        // contenía SVG subidos antes de esta comprobación.
        String tipo = detectarTipoImagen(contenido);
        String extension = tipo == null ? null : EXTENSION_POR_TIPO.get(tipo);
        if (extension == null) {
            // El mensaje dice qué se admite porque un rechazo sin explicación
            // lleva a probar suerte con otros formatos.
            throw new BadRequestException("El archivo no es una imagen válida. Se admiten PNG, JPG, "
                    + "GIF y WebP. Los vectoriales como SVG no se aceptan porque pueden ejecutar "
                    + "código en el navegador.");
        }

        try {
            // Los recursos de marca van a la zona publica: se ven en el login,
            // sin sesion, y ademas los descarga el servidor al generar informes.
            Path dir = Paths.get(uploadDir).toAbsolutePath().normalize()
                    .resolve(UploadZones.PUBLICO);
            Files.createDirectories(dir);
            // El nombre lo compone el servidor con la extensión del contenido.
            // El prefijo es una constante interna y el UUID hace el archivo
            // único, así que no hay forma de que dos subidas se pisen.
            String fileName = prefix + "_" + java.util.UUID.randomUUID() + extension;
            Path target = dir.resolve(fileName);
            Files.write(target, contenido);

            Map<String, String> res = new HashMap<>();
            res.put("url", PUBLIC_UPLOAD_PREFIX + fileName);
            return res;
        } catch (IOException e) {
            throw new BadRequestException("No se pudo guardar la imagen: " + e.getMessage());
        }
    }

    @Transactional
    public Map<String, String> saveLogo(MultipartFile file) {
        Map<String, String> res = saveImage(file, "logo");
        String relative = res.get("url");
        BrandingConfig config = getBranding();
        config.setLogoUrl(relative);
        brandingConfigRepository.save(config);
        res.put("url", relative);
        return res;
    }

    /** Logo que se muestra en la pantalla de aviso de mantenimiento. */
    @Transactional
    public Map<String, String> saveMaintenanceLogo(MultipartFile file) {
        Map<String, String> res = saveImage(file, "mantenimiento_logo");
        BrandingConfig config = getBranding();
        config.setMaintenanceLogoUrl(res.get("url"));
        brandingConfigRepository.save(config);
        return res;
    }

    @Transactional
    public Map<String, String> saveBackground(MultipartFile file) {
        Map<String, String> res = saveImage(file, "fondo");
        String relative = res.get("url");
        BrandingConfig config = getBranding();
        config.setBackgroundUrl(relative);
        brandingConfigRepository.save(config);
        res.put("url", relative);
        return res;
    }

    @Transactional
    public Map<String, String> saveLogoLogin(MultipartFile file) {
        Map<String, String> res = saveImage(file, "logo_login");
        BrandingConfig config = getBranding();
        config.setLogoLoginUrl(res.get("url"));
        brandingConfigRepository.save(config);
        return res;
    }

    @Transactional
    public Map<String, String> saveSystemIcon(MultipartFile file) {
        Map<String, String> res = saveImage(file, "system_icon");
        BrandingConfig config = getBranding();
        config.setSystemIconUrl(res.get("url"));
        brandingConfigRepository.save(config);
        return res;
    }

    /**
     * Imagen central de la pantalla de carga. Se guarda en la zona publica
     * porque se ve antes de que exista sesion, y de paso se selecciona el
     * modo IMAGE para que el administrador vea el cambio al instante.
     */
    @Transactional
    public Map<String, String> saveLoadingImage(MultipartFile file) {
        Map<String, String> res = saveImage(file, "loading");
        BrandingConfig config = getBranding();
        config.setLoadingImageUrl(res.get("url"));
        config.setLoadingMode("IMAGE");
        // Subir una imagen significa querer verla sola, no con un "Cargando..."
        // encima, asi que se activa el modo solo marca.
        config.setLoadingSoloMarca(true);
        brandingConfigRepository.save(config);
        return res;
    }

    @Transactional
    public Map<String, String> saveReportLogo(MultipartFile file) {
        Map<String, String> res = saveImage(file, "report_logo");
        BrandingConfig config = getBranding();
        config.setReportLogoUrl(res.get("url"));
        brandingConfigRepository.save(config);
        return res;
    }

    @Transactional
    public Map<String, String> saveProfileCover(MultipartFile file) {
        Map<String, String> res = saveImage(file, "perfil_portada");
        BrandingConfig config = getBranding();
        config.setProfileCoverUrl(res.get("url"));
        brandingConfigRepository.save(config);
        return res;
    }

    @Transactional
    public Map<String, String> saveChatBackground(MultipartFile file, String mode) {
        String requestedMode = mode == null ? "light" : mode.trim();
        boolean lightMode = requestedMode.equalsIgnoreCase("light");
        if (!lightMode && !requestedMode.equalsIgnoreCase("dark")) {
            throw new BadRequestException("El modo del fondo debe ser light o dark");
        }
        String normalizedMode = lightMode ? "light" : "dark";
        Map<String, String> res = saveImage(file, "chat_fondo_" + normalizedMode);
        BrandingConfig config = getBranding();
        if (normalizedMode.equals("light")) {
            config.setChatBackgroundLightUrl(res.get("url"));
            config.setChatBackgroundUrl(res.get("url"));
        } else {
            config.setChatBackgroundDarkUrl(res.get("url"));
        }
        brandingConfigRepository.save(config);
        return res;
    }

    // ============================ ÁREAS ============================

    @Transactional(readOnly = true)
    public List<Area> getAreas() {
        return areaRepository.findByDeletedAreaFalseOrderByPriorityLevelDesc();
    }

    /**
     * Nombre con el que queda un registro borrado (borrado lógico). La columna
     * del nombre es UNIQUE e incluye los borrados: sin esto, el nombre de un
     * área, categoría, equipo o tipo eliminado no se podía volver a usar (500).
     */
    static String nombreDeEliminado(String nombre, int max) {
        String marca = " (eliminado " + java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                .format(java.time.LocalDateTime.now()) + ")";
        String base = nombre == null ? "" : nombre;
        if (base.length() + marca.length() > max) {
            base = base.substring(0, Math.max(0, max - marca.length()));
        }
        return base + marca;
    }

    private static final java.util.regex.Pattern SUFIJO_ELIMINADO =
            java.util.regex.Pattern.compile(" \\(eliminado \\d{14}\\)$");

    /**
     * Nombre para mostrar: "Atención (eliminado 20261112155109)" → "Atención
     * (eliminada)". El sufijo con fecha solo existe para liberar el nombre.
     */
    public static String nombreVisible(String nombre) {
        if (nombre == null) return null;
        return SUFIJO_ELIMINADO.matcher(nombre).find()
                ? SUFIJO_ELIMINADO.matcher(nombre).replaceAll(" (eliminada)") : nombre;
    }

    /** Nombre limpio y obligatorio (espacios repetidos fuera). */
    static String nombreObligatorio(String nombre, String que, int max) {
        String n = nombre == null ? "" : nombre.strip().replaceAll("\\s+", " ");
        if (n.isEmpty()) {
            throw new BadRequestException("Escriba el nombre " + que);
        }
        if (n.length() > max) {
            throw new BadRequestException("El nombre admite hasta " + max + " caracteres");
        }
        return n;
    }

    private static boolean mismoNombre(String a, String b) {
        return a != null && b != null && a.strip().equalsIgnoreCase(b.strip());
    }

    /** Escala de prioridad de un área: 1 (baja) a 10 (alta). */
    public static final int PRIORIDAD_AREA_MIN = 1;
    public static final int PRIORIDAD_AREA_MAX = 10;
    private static final java.util.regex.Pattern COLOR_HEX = java.util.regex.Pattern.compile("^#[0-9a-fA-F]{6}$");
    private static final java.util.regex.Pattern ICONO_FA =
            java.util.regex.Pattern.compile("^fa-(solid|regular|brands) fa-[a-z0-9-]+$");

    public Area saveArea(UUID id, String name, String description, Integer priorityLevel, Boolean status,
                         String icon, String color) {
        Area area = id != null
                ? areaRepository.findById(id).filter(a -> !a.getDeletedArea())
                        .orElseThrow(() -> new ResourceNotFoundException("Área", "id", id))
                : new Area();
        boolean nueva = area.getCreatedAtArea() == null;
        String nombre = name == null ? null : name.strip().replaceAll("\\s+", " ");
        if (nueva && (nombre == null || nombre.isEmpty())) {
            throw new BadRequestException("Escriba el nombre del área");
        }
        if (nombre != null) {
            if (nombre.isEmpty()) {
                throw new BadRequestException("Escriba el nombre del área");
            }
            if (nombre.length() > 100) {
                throw new BadRequestException("El nombre admite hasta 100 caracteres");
            }
            // Sin distinguir mayúsculas: "tecnología" y "Tecnología" se ven iguales en la tabla.
            String clave = nombre.toLowerCase(java.util.Locale.ROOT);
            boolean repetida = areaRepository.findByDeletedAreaFalseOrderByPriorityLevelDesc().stream()
                    .anyMatch(a -> !a.getIdArea().equals(area.getIdArea())
                            && a.getNameArea() != null
                            && a.getNameArea().strip().toLowerCase(java.util.Locale.ROOT).equals(clave));
            if (repetida) {
                throw new BadRequestException("Ya existe un área llamada \"" + nombre + "\"");
            }
            area.setNameArea(nombre);
        }
        if (description != null) {
            String d = description.strip();
            if (d.length() > 255) {
                throw new BadRequestException("La descripción admite hasta 255 caracteres");
            }
            area.setDescriptionArea(d.isEmpty() ? null : d);
        }
        if (icon != null) {
            String i = icon.strip();
            if (!i.isEmpty() && !ICONO_FA.matcher(i).matches()) {
                throw new BadRequestException("Icono no válido: elíjalo de la lista");
            }
            area.setIconArea(i.isEmpty() ? null : i);
        }
        if (color != null) {
            String c = color.strip();
            if (!c.isEmpty() && !COLOR_HEX.matcher(c).matches()) {
                throw new BadRequestException("El color debe tener la forma #RRGGBB");
            }
            area.setColorArea(c.isEmpty() ? null : c);
        }
        if (priorityLevel != null) {
            if (priorityLevel < PRIORIDAD_AREA_MIN || priorityLevel > PRIORIDAD_AREA_MAX) {
                throw new BadRequestException("La prioridad va de " + PRIORIDAD_AREA_MIN + " a " + PRIORIDAD_AREA_MAX);
            }
            area.setPriorityLevel(priorityLevel);
        }
        if (status != null) area.setStatusArea(status);
        return areaRepository.save(area);
    }

    /** Lo que todavía apunta a un área: personas, equipos, categorías, tickets abiertos y proyectos. */
    public record UsoArea(long usuarios, long equipos, long categorias, long ticketsAbiertos, long proyectos) {
        public boolean enUso() {
            return usuarios + equipos + categorias + ticketsAbiertos + proyectos > 0;
        }

        public String resumen() {
            List<String> p = new ArrayList<>();
            if (usuarios > 0) p.add(usuarios + (usuarios == 1 ? " persona" : " personas"));
            if (equipos > 0) p.add(equipos + (equipos == 1 ? " equipo" : " equipos"));
            if (categorias > 0) p.add(categorias + (categorias == 1 ? " categoría" : " categorías"));
            if (ticketsAbiertos > 0) p.add(ticketsAbiertos + (ticketsAbiertos == 1 ? " ticket abierto" : " tickets abiertos"));
            if (proyectos > 0) p.add(proyectos + (proyectos == 1 ? " proyecto" : " proyectos"));
            return String.join(", ", p);
        }
    }

    private static final String TICKET_ABIERTO =
            " AND t.deleted_ticket = FALSE AND t.status_ticket NOT IN "
            + "(SELECT id_status FROM status WHERE upper(name_status) IN ('RESUELTO','CERRADO'))";

    @Transactional(readOnly = true)
    public UsoArea usoDeArea(UUID id) {
        if (jdbcTemplateIa == null) {
            return new UsoArea(0, 0, 0, 0, 0);
        }
        return new UsoArea(
                contar("SELECT count(*) FROM users WHERE deleted_user = FALSE AND area_id = ?", id),
                contar("SELECT count(*) FROM support_team WHERE active_team = TRUE AND area_team = ?", id),
                contar("SELECT count(*) FROM category WHERE deleted_category = FALSE AND area_category = ?", id),
                contar("SELECT count(*) FROM ticket t WHERE (t.area_destino_ticket = ? OR t.area_ticket = ?)" + TICKET_ABIERTO, id, id),
                contar("SELECT count(*) FROM proyecto WHERE deleted_proyecto = FALSE AND area_proyecto = ? "
                        + "AND estado_proyecto NOT IN ('TERMINADO','CANCELADO')", id));
    }

    private long contar(String sql, Object... args) {
        Long n = jdbcTemplateIa.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    /**
     * Borrado lógico. Si el área está en uso exige otra área a la que pasar
     * personas, equipos, categorías, tickets abiertos y proyectos; sin ella
     * responde 400 con el detalle (antes quedaban referencias huérfanas).
     */
    public void deleteArea(UUID id, UUID reasignarA) {
        Area area = areaRepository.findById(id).filter(a -> !a.getDeletedArea())
                .orElseThrow(() -> new ResourceNotFoundException("Área", "id", id));
        UsoArea uso = usoDeArea(id);
        if (uso.enUso()) {
            if (reasignarA == null) {
                throw new BadRequestException("El área está en uso (" + uso.resumen()
                        + "). Elija a qué área pasarlos antes de eliminarla.");
            }
            if (reasignarA.equals(id)) {
                throw new BadRequestException("Elija un área distinta para reasignar");
            }
            areaRepository.findById(reasignarA).filter(a -> !a.getDeletedArea() && Boolean.TRUE.equals(a.getStatusArea()))
                    .orElseThrow(() -> new BadRequestException("El área de destino no existe o está inactiva"));
            jdbcTemplateIa.update("UPDATE users SET area_id = ? WHERE area_id = ? AND deleted_user = FALSE", reasignarA, id);
            jdbcTemplateIa.update("UPDATE support_team SET area_team = ? WHERE area_team = ?", reasignarA, id);
            jdbcTemplateIa.update("UPDATE category SET area_category = ? WHERE area_category = ?", reasignarA, id);
            jdbcTemplateIa.update("UPDATE ticket t SET area_destino_ticket = ? WHERE t.area_destino_ticket = ?" + TICKET_ABIERTO, reasignarA, id);
            jdbcTemplateIa.update("UPDATE ticket t SET area_ticket = ? WHERE t.area_ticket = ?" + TICKET_ABIERTO, reasignarA, id);
            jdbcTemplateIa.update("UPDATE proyecto SET area_proyecto = ? WHERE area_proyecto = ? AND deleted_proyecto = FALSE", reasignarA, id);
        }
        area.setDeletedArea(true);
        area.setStatusArea(false);
        area.setNameArea(nombreDeEliminado(area.getNameArea(), 100));
        areaRepository.save(area);
    }

    // ============================ SUCURSALES ============================

    @Transactional(readOnly = true)
    public List<Sucursal> getSucursales() {
        return sucursalRepository.findByDeletedSucursalFalseOrderByNameSucursalAsc();
    }

    /**
     * Alta o edición de una sucursal. El borrado es lógico: los tickets y las
     * personas que apuntan a ella tienen que seguir siendo legibles aunque la
     * sucursal se deje de usar.
     */
    public Sucursal saveSucursal(UUID id, String code, String name, String description,
                                 String address, String phone, String icon, String color,
                                 Boolean status) {
        Sucursal sucursal = id != null
                ? sucursalRepository.findById(id).filter(s -> !s.getDeletedSucursal())
                        .orElseThrow(() -> new ResourceNotFoundException("Sucursal", "id", id))
                : new Sucursal();
        boolean esNueva = sucursal.getCreatedAtSucursal() == null;

        if (code != null) {
            String limpio = code.trim().toUpperCase();
            // El codigo es la clave corta de los reportes, asi que no puede
            // repetirse. Solo se rechaza si pertenece a OTRA sucursal: al editar
            // una, su propio codigo no cuenta como duplicado.
            Optional<Sucursal> conEseCodigo = sucursalRepository.findByCodeSucursal(limpio);
            if (conEseCodigo.isPresent()
                    && !conEseCodigo.get().getIdSucursal().equals(sucursal.getIdSucursal())) {
                throw new BadRequestException("El código de sucursal ya existe: " + limpio);
            }
            sucursal.setCodeSucursal(limpio);
        }
        if (name != null) {
            if (esNueva && sucursalRepository.existsByNameSucursal(name.trim())) {
                throw new BadRequestException("La sucursal ya existe: " + name);
            }
            sucursal.setNameSucursal(name.trim());
        }
        if (description != null) sucursal.setDescriptionSucursal(description);
        if (address != null) sucursal.setAddressSucursal(address);
        // Solo se valida si cambia: un teléfono viejo mal escrito no impide editar lo demás.
        if (phone != null && !phone.trim().equals(java.util.Objects.toString(sucursal.getPhoneSucursal(), ""))) {
            sucursal.setPhoneSucursal(UserService.telefonoValido(phone));
        }
        if (icon != null) sucursal.setIconSucursal(blankToNull(icon));
        if (color != null) sucursal.setColorSucursal(blankToNull(color));
        if (status != null) sucursal.setStatusSucursal(status);

        return sucursalRepository.save(sucursal);
    }

    /**
     * Borra lógicamente la sucursal. Se comprueba antes si algo la referencia, para
     * avisar en vez de dejar tickets y usuarios apuntando a un sitio que ya no
     * existe en la lista.
     */
    @Transactional
    public void deleteSucursal(UUID id) {
        Sucursal sucursal = sucursalRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sucursal", "id", id));
        long usuarios = userRepository.countBySucursal_IdSucursalAndDeletedUserFalse(id);
        if (usuarios > 0) {
            throw new BadRequestException(
                    "La sucursal tiene " + usuarios + " persona(s) asignada(s). "
                    + "Reasígnalas antes de eliminarla, o desactívala en vez de borrarla.");
        }
        // Aunque no queden personas, puede haber tickets históricos apuntando a la
        // sucursal: si se borrara en cascada se perderían, así que se avisa igual.
        long tickets = ticketRepository.countBySucursalTicket_IdSucursalAndDeletedTicketFalse(id);
        if (tickets > 0) {
            throw new BadRequestException(
                    "La sucursal tiene " + tickets + " ticket(s) asociado(s). "
                    + "Desactívala en vez de eliminarla para conservar el historial.");
        }
        sucursal.setDeletedSucursal(true);
        sucursal.setStatusSucursal(false);
        sucursalRepository.save(sucursal);
    }

    // ============================ CATEGORÍAS ============================

    @Transactional(readOnly = true)
    public List<Category> getCategories() {
        return categoryRepository.findByDeletedCategoryFalse();
    }

    @Transactional(readOnly = true)
    /**
     * Exige que el área esté en el ámbito (null = súper admin, sin límite). El
     * jefe solo gestiona lo de sus áreas.
     */
    public void exigirAmbito(java.util.Set<UUID> ambito, UUID areaId, String que) {
        if (ambito != null && (areaId == null || !ambito.contains(areaId))) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Solo puede " + que + " de las áreas a su cargo");
        }
    }

    @Transactional(readOnly = true)
    public UUID areaDeEquipo(UUID teamId) {
        return teamId == null ? null : teamRepository.findById(teamId).map(SupportTeam::getAreaId).orElse(null);
    }

    @Transactional(readOnly = true)
    public UUID areaDeCategoria(UUID categoryId) {
        return categoryId == null ? null : categoryRepository.findById(categoryId).map(Category::getAreaId).orElse(null);
    }

    @Transactional(readOnly = true)
    public UUID areaDePoliticaSla(UUID policyId) {
        return policyId == null ? null : slaPolicyRepository.findById(policyId)
                .map(SlaPolicy::getAreaSlaPolicy).orElse(null);
    }

    @Transactional(readOnly = true)
    public UUID areaDeReglaSla(UUID ruleId) {
        return ruleId == null ? null : slaRuleRepository.findById(ruleId)
                .map(r -> r.getArea() == null ? null : r.getArea().getIdArea()).orElse(null);
    }

    @Transactional(readOnly = true)
    public UUID areaDeUsuario(UUID userId) {
        return userId == null ? null : userRepository.findById(userId)
                .map(u -> u.getArea() == null ? null : u.getArea().getIdArea()).orElse(null);
    }

    public List<Map<String, Object>> getCategoriesForAdmin() {
        return categoryRepository.findByDeletedCategoryFalse().stream().map(this::vistaCategoria).toList();
    }

    /**
     * Lo que la pantalla necesita de una categoría. Crear y editar devolvían la
     * entidad con sus relaciones perezosas sin cargar y Jackson fallaba al
     * escribirla (500 "ByteBuddyInterceptor"), aunque el cambio sí se guardaba.
     */
    public Map<String, Object> vistaCategoria(Category c) {
        {
            Map<String, Object> m = new HashMap<>();
            m.put("idCategory", c.getIdCategory());
            m.put("nameCategory", c.getNameCategory());
            m.put("descriptionCategory", c.getDescriptionCategory());
            m.put("iconCategory", c.getIconCategory());
            m.put("colorCategory", c.getColorCategory());
            Map<String, Object> team = new HashMap<>();
            team.put("idTeam", c.getTeamCategory() != null ? c.getTeamCategory().getIdTeam() : null);
            team.put("nameTeam", c.getTeamCategory() != null ? c.getTeamCategory().getNameTeam() : null);
            m.put("teamCategory", team);
            m.put("areaId", c.getAreaId());
            m.put("area", c.getAreaCategory() != null ? c.getAreaCategory().getNameArea() : null);
            return m;
        }
    }

    /** Guarda y devuelve la vista (dentro de la transacción del servicio). */
    public Map<String, Object> guardarCategoria(UUID id, String name, String description, UUID teamId,
                                                String icon, String color, UUID areaId) {
        return vistaCategoria(saveCategory(id, name, description, teamId, icon, color, areaId));
    }

    public Category saveCategory(UUID id, String name, String description, UUID teamId,
                                 String icon, String color) {
        return saveCategory(id, name, description, teamId, icon, color, null);
    }

    /** Con área (V28). Si no se indica, la categoría toma la de su equipo. */
    public Category saveCategory(UUID id, String name, String description, UUID teamId,
                                 String icon, String color, UUID areaId) {
        Category category = id != null
                ? categoryRepository.findById(id).filter(c -> !c.getDeletedCategory())
                        .orElseThrow(() -> new ResourceNotFoundException("Categoría", "id", id))
                : new Category();
        if (category.getIdCategory() == null || name != null) {
            String nombre = nombreObligatorio(name, "de la categoría", 100);
            UUID propia = category.getIdCategory();
            boolean repetida = categoryRepository.findByDeletedCategoryFalse().stream()
                    .anyMatch(x -> !x.getIdCategory().equals(propia) && mismoNombre(x.getNameCategory(), nombre));
            if (repetida) {
                throw new BadRequestException("Ya existe una categoría llamada \"" + nombre + "\"");
            }
            category.setNameCategory(nombre);
        }
        if (description != null) category.setDescriptionCategory(description);
        if (icon != null) category.setIconCategory(icon.isBlank() ? null : icon.trim());
        if (color != null) category.setColorCategory(color.isBlank() ? null : color.trim());
        if (teamId != null) {
            category.setTeamCategory(teamRepository.findById(teamId)
                    .orElseThrow(() -> new ResourceNotFoundException("Equipo", "id", teamId)));
        }
        if (areaId != null) {
            category.setAreaCategory(areaRepository.findById(areaId)
                    .orElseThrow(() -> new ResourceNotFoundException("Área", "id", areaId)));
        } else if (category.getTeamCategory() != null && category.getTeamCategory().getAreaTeam() != null) {
            // Sin área explícita, la categoría es del área de su equipo: así
            // cambiar de equipo la mueve con él en lugar de chocar con la regla
            // de abajo.
            category.setAreaCategory(category.getTeamCategory().getAreaTeam());
        }
        if (category.getAreaCategory() != null && category.getTeamCategory() != null
                && category.getTeamCategory().getAreaTeam() != null
                && !category.getTeamCategory().getAreaTeam().getIdArea().equals(category.getAreaCategory().getIdArea())) {
            throw new BadRequestException("El equipo de la categoría tiene que ser de la misma área");
        }
        if (category.getTeamCategory() == null) {
            throw new BadRequestException("Elija el equipo que atiende la categoría");
        }
        return categoryRepository.save(category);
    }

    public void deleteCategory(UUID id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Categoría", "id", id));
        category.setDeletedCategory(true);
        category.setNameCategory(nombreDeEliminado(category.getNameCategory(), 100));
        categoryRepository.save(category);
    }

    // ============================ EQUIPOS ============================

    /** Perezoso: RoutingService no debe formar un ciclo de arranque con este servicio. */
    @Autowired
    @org.springframework.context.annotation.Lazy
    private RoutingService routingService;

    @Transactional(readOnly = true)
    public List<SupportTeam> getTeams() {
        return teamRepository.findByActiveTeamTrue();
    }

    public SupportTeam saveTeam(UUID id, String name, String description, String method, Boolean active,
                                String icon, String color) {
        return saveTeam(id, name, description, method, active, icon, color, null);
    }

    public SupportTeam saveTeam(UUID id, String name, String description, String method, Boolean active,
                                String icon, String color, Boolean autoMembers) {
        return saveTeam(id, name, description, method, active, icon, color, autoMembers, null);
    }

    /** Con área (V28): los equipos atienden los tickets de su área. */
    public SupportTeam saveTeam(UUID id, String name, String description, String method, Boolean active,
                                String icon, String color, Boolean autoMembers, UUID areaId) {
        SupportTeam team = id != null
                ? teamRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Equipo", "id", id))
                : new SupportTeam();
        if (team.getIdTeam() == null || name != null) {
            String nombre = nombreObligatorio(name, "del equipo", 100);
            UUID propio = team.getIdTeam();
            boolean repetido = teamRepository.findAll().stream()
                    .anyMatch(x -> !x.getIdTeam().equals(propio) && mismoNombre(x.getNameTeam(), nombre));
            if (repetido) {
                throw new BadRequestException("Ya existe un equipo llamado \"" + nombre + "\"");
            }
            team.setNameTeam(nombre);
        }
        if (description != null) team.setDescriptionTeam(description);
        if (method != null) team.setAssignmentMethod(method);
        if (active != null) team.setActiveTeam(active);
        if (icon != null) team.setIconTeam(icon.isBlank() ? null : icon.trim());
        if (color != null) team.setColorTeam(color.isBlank() ? null : color.trim());
        if (autoMembers != null) team.setAutoMembersTeam(autoMembers);
        if (areaId != null) {
            team.setAreaTeam(areaRepository.findById(areaId)
                    .orElseThrow(() -> new ResourceNotFoundException("Área", "id", areaId)));
        }
        SupportTeam guardado = teamRepository.save(team);
        if (Boolean.TRUE.equals(guardado.getAutoMembersTeam()) && routingService != null) {
            // Se llenan ya para que el panel de miembros los muestre.
            routingService.sincronizarAgentes(guardado);
        }
        return guardado;
    }

    /**
     * Eliminar un equipo = desactivarlo y liberar su nombre (los tickets
     * cerrados siguen apuntando a él). Antes no había ruta DELETE y respondía 405.
     * No se permite si todavía atiende categorías o tickets abiertos.
     */
    public void deleteTeam(UUID id) {
        SupportTeam team = teamRepository.findById(id)
                .filter(t -> Boolean.TRUE.equals(t.getActiveTeam()))
                .orElseThrow(() -> new ResourceNotFoundException("Equipo", "id", id));
        long categorias = categoryRepository.findByDeletedCategoryFalse().stream()
                .filter(c -> c.getTeamCategory() != null && id.equals(c.getTeamCategory().getIdTeam()))
                .count();
        if (categorias > 0) {
            throw new BadRequestException("El equipo atiende " + categorias
                    + (categorias == 1 ? " categoría" : " categorías") + ": páselas a otro equipo antes de eliminarlo");
        }
        if (jdbcTemplateIa != null) {
            Long abiertos = jdbcTemplateIa.queryForObject("SELECT count(*) FROM ticket t WHERE t.team_ticket = ?"
                    + TICKET_ABIERTO, Long.class, id);
            if (abiertos != null && abiertos > 0) {
                throw new BadRequestException("El equipo tiene " + abiertos
                        + (abiertos == 1 ? " ticket abierto" : " tickets abiertos") + ": reasígnelos antes de eliminarlo");
            }
            jdbcTemplateIa.update("UPDATE team_user SET active_team_user = FALSE WHERE team_id = ?", id);
        }
        team.setActiveTeam(false);
        team.setNameTeam(nombreDeEliminado(team.getNameTeam(), 100));
        teamRepository.save(team);
    }

    public void addMemberToTeam(UUID teamId, UUID userId) {
        if (teamUserRepository.existsByTeamIdTeamAndUserIdUser(teamId, userId)) {
            teamUserRepository.findByTeamIdTeamAndUserIdUser(teamId, userId)
                    .ifPresent(tu -> tu.setActiveTeamUser(true));
            return;
        }
        TeamUser tu = new TeamUser();
        tu.setTeam(teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Equipo", "id", teamId)));
        tu.setUser(userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", "id", userId)));
        tu.setActiveTeamUser(true);
        teamUserRepository.save(tu);
    }

    public void removeMemberFromTeam(UUID teamId, UUID userId) {
        teamUserRepository.findByTeamIdTeamAndUserIdUser(teamId, userId)
                .ifPresent(tu -> {
                    tu.setActiveTeamUser(false);
                    teamUserRepository.save(tu);
                });
    }

    // ============================ SLA ============================

    @Transactional(readOnly = true)
    public List<SlaPolicy> getSlaPolicies() {
        return slaPolicyRepository.findByActiveSlaPolicyTrue();
    }

    public SlaPolicy saveSlaPolicy(UUID id, String name, String description,
                                   Integer responseMinutes, Integer resolutionMinutes, Boolean active) {
        return saveSlaPolicy(id, name, description, responseMinutes, resolutionMinutes, active, null, null);
    }

    public SlaPolicy saveSlaPolicy(UUID id, String name, String description,
                                   Integer responseMinutes, Integer resolutionMinutes, Boolean active,
                                   String icon, String color) {
        return saveSlaPolicy(id, name, description, responseMinutes, resolutionMinutes, active, icon, color, null, false);
    }

    /**
     * Guarda una política. {@code cambiarArea} indica si {@code areaId} se debe
     * aplicar (null = global); el controlador ya comprobó el ámbito del jefe.
     */
    public SlaPolicy saveSlaPolicy(UUID id, String name, String description,
                                   Integer responseMinutes, Integer resolutionMinutes, Boolean active,
                                   String icon, String color, UUID areaId, boolean cambiarArea) {
        SlaPolicy policy = id != null
                ? slaPolicyRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("SLA", "id", id))
                : new SlaPolicy();
        // Lo valida la base (chk_response_gt0/chk_resolution_gt0), pero ahí salía
        // como error 500; aquí se responde 400 con un mensaje que se entiende.
        String nombre = name == null ? null : name.strip();
        if (policy.getIdSlaPolicy() == null && (nombre == null || nombre.isEmpty())) {
            throw new BadRequestException("Escriba el nombre de la política");
        }
        if (responseMinutes != null && responseMinutes <= 0) {
            throw new BadRequestException("El tiempo de respuesta debe ser mayor que 0 minutos");
        }
        if (resolutionMinutes != null && resolutionMinutes <= 0) {
            throw new BadRequestException("El tiempo de resolución debe ser mayor que 0 minutos");
        }
        Integer respuesta = responseMinutes != null ? responseMinutes : policy.getResponseTimeMinutes();
        Integer resolucion = resolutionMinutes != null ? resolutionMinutes : policy.getResolutionTimeMinutes();
        if (respuesta != null && resolucion != null && resolucion < respuesta) {
            throw new BadRequestException("La resolución no puede vencer antes que la primera respuesta");
        }
        if (nombre != null && !nombre.isEmpty()) {
            // Igual que áreas, equipos y categorías: sin distinguir mayúsculas
            // ("sla crítico" y "SLA Crítico" son la misma política).
            UUID propia = policy.getIdSlaPolicy();
            slaPolicyRepository.findAll().stream()
                    .filter(x -> !x.getIdSlaPolicy().equals(propia) && mismoNombre(x.getNameSlaPolicy(), nombre))
                    .findFirst()
                    .ifPresent(x -> {
                        throw new BadRequestException("Ya existe una política SLA llamada \"" + x.getNameSlaPolicy() + "\""
                                + (Boolean.FALSE.equals(x.getActiveSlaPolicy()) ? " (inactiva: actívela en lugar de crear otra)" : ""));
                    });
            policy.setNameSlaPolicy(nombre);
        }
        if (description != null) policy.setDescriptionSlaPolicy(description);
        if (responseMinutes != null) policy.setResponseTimeMinutes(responseMinutes);
        if (resolutionMinutes != null) policy.setResolutionTimeMinutes(resolutionMinutes);
        if (active != null) policy.setActiveSlaPolicy(active);
        if (icon != null) policy.setIconSlaPolicy(blankToNull(icon));
        if (color != null) policy.setColorSlaPolicy(blankToNull(color));
        if (cambiarArea) policy.setAreaSlaPolicy(areaId);
        return slaPolicyRepository.save(policy);
    }

    /**
     * Si la política cuenta solo en horario laboral (y fuera de feriados) o las
     * 24 horas (V49). Vale para los tickets nuevos y para los que se recalculan.
     */
    public SlaPolicy fijarHorarioLaboral(UUID policyId, Boolean horarioLaboral) {
        SlaPolicy policy = slaPolicyRepository.findById(policyId)
                .orElseThrow(() -> new ResourceNotFoundException("SLA", "id", policyId));
        policy.setHorarioLaboralSlaPolicy(!Boolean.FALSE.equals(horarioLaboral));
        return slaPolicyRepository.save(policy);
    }

    public SlaRule saveSlaRule(UUID id, UUID policyId, UUID areaId, UUID priorityId,
                               UUID categoryId, UUID typeId, Boolean active) {
        SlaRule rule = id != null
                ? slaRuleRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Regla SLA", "id", id))
                : new SlaRule();
        SlaPolicy politica = slaPolicyRepository.findById(policyId)
                .orElseThrow(() -> new ResourceNotFoundException("Política SLA", "id", policyId));
        // Una regla de un área solo usa políticas globales o de esa misma área.
        if (politica.getAreaSlaPolicy() != null && !politica.getAreaSlaPolicy().equals(areaId)) {
            throw new BadRequestException("La política \"" + politica.getNameSlaPolicy()
                    + "\" es de otra área: elija una global o del área de la regla");
        }
        rule.setSlaPolicy(politica);
        rule.setArea(areaRepository.findById(areaId)
                .orElseThrow(() -> new ResourceNotFoundException("Área", "id", areaId)));
        rule.setPriority(priorityRepository.findById(priorityId)
                .orElseThrow(() -> new ResourceNotFoundException("Prioridad", "id", priorityId)));
        if (categoryId != null) {
            rule.setCategory(categoryRepository.findById(categoryId)
                    .orElseThrow(() -> new ResourceNotFoundException("Categoría", "id", categoryId)));
        } else {
            rule.setCategory(null);
        }
        if (typeId != null) {
            rule.setType(ticketTypeRepository.findById(typeId)
                    .filter(t -> !t.getDeletedTicketType())
                    .orElseThrow(() -> new ResourceNotFoundException("Tipo de ticket", "id", typeId)));
        } else {
            rule.setType(null);
        }
        if (active != null) rule.setActiveSlaRule(active);
        return slaRuleRepository.save(rule);
    }

    @Transactional(readOnly = true)
    public List<SlaRule> getSlaRules() {
        return slaRuleRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getSlaRulesForAdmin() {
        return slaRuleRepository.findAll().stream().map(r -> {
            Map<String, Object> m = new HashMap<>();
            m.put("idSlaRule", r.getIdSlaRule());
            m.put("slaPolicyId", r.getSlaPolicy() != null ? r.getSlaPolicy().getIdSlaPolicy() : null);
            // El jefe no lista las políticas globales, pero sus reglas antiguas
            // pueden usarlas: el nombre viaja con la regla.
            m.put("slaPolicyName", r.getSlaPolicy() != null ? r.getSlaPolicy().getNameSlaPolicy() : null);
            m.put("areaId", r.getArea() != null ? r.getArea().getIdArea() : null);
            m.put("priorityId", r.getPriority() != null ? r.getPriority().getIdPriority() : null);
            m.put("categoryId", r.getCategory() != null ? r.getCategory().getIdCategory() : null);
            m.put("typeId", r.getType() != null ? r.getType().getIdTicketType() : null);
            m.put("activeSlaRule", r.getActiveSlaRule());
            return m;
        }).toList();
    }

    public void deleteSlaPolicy(UUID id) {
        SlaPolicy p = slaPolicyRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("SLA", "id", id));
        p.setActiveSlaPolicy(false);
        slaPolicyRepository.save(p);
    }

    public void deleteSlaRule(UUID id) {
        slaRuleRepository.deleteById(id);
    }

    // ============================ ESCALAMIENTO ============================

    @Transactional(readOnly = true)
    public List<SlaEscalation> getEscalations() {
        return escalationRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getEscalationsForAdmin() {
        return escalationRepository.findAll().stream().map(e -> {
            Map<String, Object> m = new HashMap<>();
            m.put("idEscalation", e.getIdEscalation());
            m.put("slaPolicyId", e.getSlaPolicy() != null ? e.getSlaPolicy().getIdSlaPolicy() : null);
            m.put("triggerPercentage", e.getTriggerPercentage());
            m.put("actionType", e.getActionType());
            m.put("destinationTeamId", e.getDestinationTeam() != null ? e.getDestinationTeam().getIdTeam() : null);
            m.put("destinationUserId", e.getDestinationUser() != null ? e.getDestinationUser().getIdUser() : null);
            m.put("notificationTemplateId",
                    e.getNotificationTemplate() != null ? e.getNotificationTemplate().getIdTemplate() : null);
            return m;
        }).toList();
    }

    public SlaEscalation saveEscalation(UUID id, UUID policyId, Integer trigger, String action,
                                        UUID teamId, UUID userId, UUID templateId) {
        SlaEscalation escalation = id != null
                ? escalationRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Escalamiento", "id", id))
                : new SlaEscalation();
        if (policyId != null) {
            escalation.setSlaPolicy(slaPolicyRepository.findById(policyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Política SLA", "id", policyId)));
        }
        if (trigger != null) escalation.setTriggerPercentage(trigger);
        if (action != null) escalation.setActionType(action);
        // null explícito = dejar el destino sin asignar (no buscar con id nulo).
        escalation.setDestinationTeam(teamId != null ? teamRepository.findById(teamId).orElse(null) : null);
        escalation.setDestinationUser(userId != null ? userRepository.findById(userId).orElse(null) : null);
        escalation.setNotificationTemplate(
                templateId != null ? templateRepository.findById(templateId).orElse(null) : null);
        return escalationRepository.save(escalation);
    }

    public void deleteEscalation(UUID id) {
        escalationRepository.deleteById(id);
    }

    // ============================ PLANTILLAS ============================

    @Transactional(readOnly = true)
    public List<NotificationTemplate> getTemplates() {
        return templateRepository.findAll();
    }

    public NotificationTemplate saveTemplate(UUID id, String name, String type, String subject, String body, Boolean active) {
        return saveTemplate(id, name, type, subject, body, active, null, null, null, null, null);
    }

    public NotificationTemplate saveTemplate(UUID id, String name, String type, String subject, String body,
                                             Boolean active, String recipientRule, String recipientEmail) {
        return saveTemplate(id, name, type, subject, body, active, recipientRule, recipientEmail, null, null, null);
    }

    /**
     * Guarda la plantilla. El {@code name_template} es la clave del evento que
     * dispara el envío (RESET_PASSWORD, TICKET_CREADO, ...). Si se crea una
     * plantilla con un nombre de evento que ya existe, se sobrescribe esa fila
     * en lugar de fallar: así el superadmin elige qué texto se envía.
     */
    public NotificationTemplate saveTemplate(UUID id, String name, String type, String subject, String body,
                                             Boolean active, String recipientRule, String recipientEmail,
                                             String icon, String color, String imageUrl) {
        NotificationTemplate template = id != null
                ? templateRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Plantilla", "id", id))
                : templateRepository.findByNameTemplate(name).orElseGet(NotificationTemplate::new);
        if (name != null) template.setNameTemplate(name);
        if (type != null) template.setTypeTemplate(type);
        if (subject != null) template.setSubjectTemplate(subject);
        if (body != null) template.setBodyTemplate(body);
        if (active != null) template.setActiveTemplate(active);
        if (icon != null) template.setIconTemplate(blankToNull(icon));
        if (color != null) template.setColorTemplate(blankToNull(color));
        // Editada por un administrador: los scripts de arranque la respetan.
        template.setCustomTemplate(true);
        if (imageUrl != null) {
            template.setImageUrl(blankToNull(comprobarUrlDePlantilla(imageUrl)));
        }
        if (recipientRule != null && !recipientRule.isBlank()) {
            template.setRecipientRule(recipientRule.trim());
        }
        if (recipientEmail != null) {
            template.setRecipientEmail(recipientEmail.isBlank() ? null : recipientEmail.trim());
        }
        NotificationTemplate saved = templateRepository.save(template);
        if (saved.getImageUrl() != null && !saved.getImageUrl().isBlank()) {
            vincularImagenDePlantilla(saved, saved.getImageUrl());
        }
        return saved;
    }

    public NotificationTemplate updateTemplate(UUID id, String subject, String body, Boolean active,
                                               String recipientRule, String recipientEmail,
                                               String icon, String color, String imageUrl) {
        NotificationTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plantilla", "id", id));
        if (subject != null) template.setSubjectTemplate(subject);
        if (body != null) template.setBodyTemplate(body);
        if (active != null) template.setActiveTemplate(active);
        if (icon != null) template.setIconTemplate(blankToNull(icon));
        if (color != null) template.setColorTemplate(blankToNull(color));
        // Editada por un administrador: los scripts de arranque la respetan.
        template.setCustomTemplate(true);
        if (imageUrl != null) {
            template.setImageUrl(blankToNull(comprobarUrlDePlantilla(imageUrl)));
        }
        if (recipientRule != null) {
            if (!List.of("DEFAULT", "SOLICITANTE", "AGENTE", "AMBOS", "CUSTOM").contains(recipientRule)) {
                throw new BadRequestException("Regla de destinatario inválida: " + recipientRule);
            }
            template.setRecipientRule(recipientRule);
        }
        if (recipientEmail != null) {
            template.setRecipientEmail(recipientEmail.isBlank() ? null : recipientEmail.trim());
        }
        NotificationTemplate saved = templateRepository.save(template);
        if (saved.getImageUrl() != null && !saved.getImageUrl().isBlank()) {
            vincularImagenDePlantilla(saved, saved.getImageUrl());
        }
        return saved;
    }

    /**
     * La imagen de la plantilla acaba dentro del HTML del correo, que la lee el
     * cliente de correo del destinatario: es el mismo riesgo que una imagen de
     * marca, asi que se accepts la zona pública o una dirección pública.
     */
private String comprobarUrlDePlantilla(String valor) {
        if (valor == null || valor.isBlank()) {
            return valor;
        }
        String limpio = valor.trim();
        // Las imagenes de plantilla se sirven desde la base con un token. Se
        // acepta tanto la ruta relativa como la URL absoluta que devuelve el
        // endpoint de subida, que es lo que el formulario reenvia al guardar.
        if (esRutaDeImagenDePlantilla(limpio)) {
            return limpio;
        }
        limpio = normalizarUrlDeMarca(limpio);
        if (!limpio.toLowerCase().startsWith("http")) {
            if (!limpio.startsWith(PUBLIC_UPLOAD_PREFIX)) {
                throw new BadRequestException("La imagen de la plantilla debe ser una ruta "
                        + PUBLIC_UPLOAD_PREFIX + "... o una dirección http(s).");
            }
            return limpio;
        }
        urlSafetyValidator.exigirUrlPublica(limpio, "La imagen de la plantilla");
        return limpio;
    }

/**
 * Guarda la imagen de una plantilla en la base y devuelve la ruta con la que se
 * sirve. No se usa la zona de archivos porque esta imagen la carga el cliente de
 * correo del destinatario, fuera del servidor, y conviene que no dependa de una
 * carpeta en disco ni quede expuesta como un recurso estatico mas.
 *
 * El tipo se comprueba contra el contenido, no contra el nombre del archivo:
 * un .png subido que en realidad sea HTML dejaría de ser una imagen al
 * guardarla. SVG queda fuera a proposito: es un documento que puede llevar
 * script, y aqui se sirve en una ruta pública.
 */
@Transactional
public Map<String, String> saveTemplateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("La imagen de la plantilla está vacía");
        }
        if (file.getSize() > TEMPLATE_IMAGE_MAX_BYTES) {
            throw new BadRequestException("La imagen no puede superar los "
                    + (TEMPLATE_IMAGE_MAX_BYTES / (1024 * 1024)) + " MB");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("No se pudo leer la imagen: " + e.getMessage());
        }
        String mime = detectarTipoImagen(bytes);
        if (mime == null) {
            throw new BadRequestException("La imagen debe ser PNG, JPG, GIF o WEBP");
        }

        NotificationTemplateImage image = new NotificationTemplateImage();
        image.setTokenImage(nuevoTokenImagen());
        image.setMimeTypeImage(mime);
        String original = file.getOriginalFilename();
        image.setOriginalNameImage(original == null ? null : original.substring(
                0, Math.min(original.length(), 255)));
        image.setDataImage(bytes);
        image.setSizeBytesImage(bytes.length);
        templateImageRepository.save(image);

        Map<String, String> res = new HashMap<>();
        res.put("url", rutaImagenPlantilla(image.getTokenImage()));
        return res;
    }

    /** Prefijo con el que se sirven las imagenes de plantilla guardadas en la base. */
    private static final String TEMPLATE_IMAGE_PREFIX = "/templates/image/";

    /** Ruta publica con la que se sirve una imagen de plantilla. */
    private String rutaImagenPlantilla(String token) {
        return TEMPLATE_IMAGE_PREFIX + token;
    }

    /**
     * Saca el token de una ruta de imagen de plantilla, venga como sea.
     *
     * El endpoint de subida devuelve la URL absoluta (http://host/api/templates/
     * image/token) porque asi la pinte el navegador, y el formulario la manda de
     * vuelta al guardar la plantilla. Si aqui solo se aceptara la forma relativa,
     * el guardado fallaria con "la imagen indicada no existe": es el mismo viaje
     * de ida y vuelta que rompia el guardado de la marca.
     *
     * Se recorta hasta el prefijo en lugar de quedarse con la ruta entera, para
     * que tambien funcione con el context-path "/api" delante.
     */
    private String tokenDeImagenPlantilla(String valor) {
        if (valor == null) {
            return null;
        }
        int indice = valor.trim().indexOf(TEMPLATE_IMAGE_PREFIX);
        if (indice < 0) {
            return null;
        }
        String token = valor.trim().substring(indice + TEMPLATE_IMAGE_PREFIX.length());
        int corte = token.length();
        for (char separador : new char[] { '?', '#' }) {
            int pos = token.indexOf(separador);
            if (pos >= 0 && pos < corte) {
                corte = pos;
            }
        }
        token = token.substring(0, corte);
        return token.matches("[0-9a-f]{32}") ? token : null;
    }

    /**
     * Bytes de una imagen de plantilla, para el endpoint publico que la usan los
     * clientes de correo. Si el token no existe se devuelve null en vez de una
     * excepcion: es una URL que puede venir en un correo viejo.
     */
    @Transactional(readOnly = true)
    public NotificationTemplateImage getTemplateImage(String token) {
        if (token == null || !token.matches("[0-9a-f]{32}")) {
            return null;
        }
        return templateImageRepository.findByTokenImage(token).orElse(null);
    }

/**
     * Comprueba que el token tenga forma de token. No es una defensa contra
     * adivinar el valor (para eso es aleatorio), sino contra que un
     * administrador pueda meter en image_url una ruta arbitraria.
     */
private boolean esRutaDeImagenDePlantilla(String valor) {
        return tokenDeImagenPlantilla(valor) != null;
    }

    /** Vincula la imagen referenciada por una ruta a la plantilla que se guarda. */
    private void vincularImagenDePlantilla(NotificationTemplate template, String imageUrl) {
        String token = tokenDeImagenPlantilla(imageUrl);
        if (token == null) {
            return;
        }
        NotificationTemplateImage image = templateImageRepository
                .findByTokenImage(token)
                .orElseThrow(() -> new BadRequestException("La imagen indicada no existe"));
        // No se deja robar una imagen de otra plantilla: si ya esta vinculada a
        // una distinta, el guardado falla en vez de moverle el bytes.
        if (image.getIdTemplate() != null && template.getIdTemplate() != null
                && !image.getIdTemplate().equals(template.getIdTemplate())) {
            throw new BadRequestException("Esa imagen ya pertenece a otra plantilla");
        }
        image.setIdTemplate(template.getIdTemplate());
        templateImageRepository.save(image);
    }

    /** Detecta el tipo real de la imagen leyendo su firma, no su extensión. */
    private String detectarTipoImagen(byte[] b) {
        if (b == null || b.length < 12) {
            return null;
        }
        int p0 = b[0] & 0xFF, p1 = b[1] & 0xFF, p2 = b[2] & 0xFF, p3 = b[3] & 0xFF;
        if (p0 == 0x89 && p1 == 0x50 && p2 == 0x4E && p3 == 0x47) {
            return "image/png";
        }
        if (p0 == 0xFF && p1 == 0xD8 && p2 == 0xFF) {
            return "image/jpeg";
        }
        // La version de GIF va en el byte 4: "GIF87a" o "GIF89a". Mirar otro
        // byte-descubriria el mismo problema con cualquier firma que no sea
        // de longitud fija.
        if (p0 == 0x47 && p1 == 0x49 && p2 == 0x46 && p3 == 0x38) {
            int version = b[4] & 0xFF;
            return (version == 0x37 || version == 0x39) ? "image/gif" : null;
        }
        // WebP es "RIFF" + 4 bytes de tamaño + "WEBP", asi que la comprobacion
        // abarca los 12 primeros bytes.
        if (p0 == 0x52 && p1 == 0x49 && p2 == 0x46 && p3 == 0x46
                && (b[8] & 0xFF) == 0x57 && (b[9] & 0xFF) == 0x45
                && (b[10] & 0xFF) == 0x42 && (b[11] & 0xFF) == 0x50) {
            return "image/webp";
        }
        return null;
    }

    private String nuevoTokenImagen() {
        byte[] bytes = new byte[16];
        new java.security.SecureRandom().nextBytes(bytes);
        StringBuilder sb = new StringBuilder(32);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Al borrar una plantilla se lleva tambien su imagen. La clave foranea tiene
     * ON DELETE CASCADE, pero se limpia de forma explicita para no dejar los
     * bytes colgando hasta que la base procese la cascada.
     */
    @Transactional
    public void deleteTemplate(UUID id) {
        templateImageRepository.deleteByIdTemplate(id);
        templateRepository.deleteById(id);
    }

    // ============================ ESCALACIONES ============================

    @Transactional(readOnly = true)
    public List<SlaEscalation> getEscalationsAll() {
        return escalationRepository.findAll();
    }

    // ============================ COMBOS ============================

    @Transactional(readOnly = true)
    public List<User> agents() {
        return userRepository.findByRoleName("AGENT");
    }

    @Transactional(readOnly = true)
    public List<com.mesaayuda.dto.MiembroEquipoResponse> teamMembers(UUID teamId) {
        return teamUserRepository.findByTeamIdTeamAndActiveTeamUserTrue(teamId).stream()
                .filter(tu -> tu.getUser() != null && !Boolean.TRUE.equals(tu.getUser().getDeletedUser()))
                .map(tu -> aMiembro(tu.getUser(), tu.getLastAssignedAt()))
                .sorted(java.util.Comparator.comparing(m -> m.nombre().toLowerCase(java.util.Locale.ROOT)))
                .toList();
    }

    /**
     * Personal que se puede añadir al equipo a mano: agentes y jefes activos del
     * área del equipo (de cualquier área si el equipo no tiene), que no estén ya.
     */
    @Transactional(readOnly = true)
    public List<com.mesaayuda.dto.MiembroEquipoResponse> candidatosDeEquipo(UUID teamId) {
        UUID area = areaDeEquipo(teamId);
        java.util.Set<UUID> yaEstan = new java.util.HashSet<>();
        teamUserRepository.findByTeamIdTeamAndActiveTeamUserTrue(teamId)
                .forEach(tu -> yaEstan.add(tu.getUser().getIdUser()));
        return userRepository.personalActivo(List.of(com.mesaayuda.security.Roles.AGENTE, com.mesaayuda.security.Roles.JEFE))
                .stream()
                .filter(u -> !yaEstan.contains(u.getIdUser()))
                .filter(u -> area == null || (u.getArea() != null && area.equals(u.getArea().getIdArea())))
                .map(u -> aMiembro(u, null))
                .toList();
    }

    private com.mesaayuda.dto.MiembroEquipoResponse aMiembro(User u, java.time.OffsetDateTime ultima) {
        String nombre = u.getDisplayName() != null && !u.getDisplayName().isBlank()
                ? u.getDisplayName() : u.getCorporateEmailUser();
        return new com.mesaayuda.dto.MiembroEquipoResponse(u.getIdUser(), nombre, u.getCorporateEmailUser(),
                u.getRolUser() == null ? null : u.getRolUser().getNameRole(),
                u.getArea() == null ? null : u.getArea().getNameArea(), ultima);
    }

    // ============================ AUDITORÍA DEL SISTEMA ============================

    @Transactional(readOnly = true)
    public List<AuditEventResponse> getAuditEvents() {
        return auditLogRepository.findTop200ByOrderByCreatedAtDesc().stream()
                .map(a -> new AuditEventResponse(
                        a.getIdAudit(),
                        a.getUsername(),
                        a.getAction(),
                        a.getEntity(),
                        a.getEntityId(),
                        a.getIpAddress(),
                        a.getCreatedAt()))
                .toList();
    }

    // ============================ CONFIGURACIÓN IA ============================

    @Transactional(readOnly = true)
    public AiConfigResponse getAiConfig() {
        AiConfig cfg = aiConfigRepository.findFirstByOrderByIdAiConfigAsc().orElseGet(AiConfig::new);
        return toAiResponse(cfg);
    }

    public AiConfigResponse saveAiConfig(AiConfigRequest request, UUID updatedBy) {
        AiConfig cfg = aiConfigRepository.findFirstByOrderByIdAiConfigAsc().orElseGet(AiConfig::new);
        cfg.setProvider(request.provider().toUpperCase());
        cfg.setModel(normalizeGeminiModel(request.provider(), request.model()));
        cfg.setBaseUrl(request.baseUrl());
        cfg.setProviderIcon(request.providerIcon());
        cfg.setEnabled(Boolean.TRUE.equals(request.enabled()));
        if (request.rulesText() != null) {
            cfg.setRulesText(request.rulesText().isBlank() ? null : request.rulesText());
        }
        if (request.enabledActions() != null && !request.enabledActions().isBlank()) {
            cfg.setEnabledActions(normalizarAccionesIa(request.enabledActions()));
        }
        if (request.dailyLimit() != null) {
            cfg.setDailyLimit(request.dailyLimit() <= 0 ? null : request.dailyLimit());
        }
        if (request.respondePrimero() != null) {
            cfg.setRespondePrimero(request.respondePrimero());
        }
if (request.apiKey() != null && !request.apiKey().isBlank()) {
              cfg.setApiKey(secretCryptoService.encrypt(request.apiKey().trim()));
          }
        cfg.setUpdatedBy(updatedBy);
        AiConfig saved = aiConfigRepository.save(cfg);
        return toAiResponse(saved);
    }

    /**
     * Normaliza la lista de acciones habilitadas de la IA y descarta lo que no
     * exista, para que un valor manipulado no deje la pantalla sin forma de
     * comprobar si una acción está activa.
     */
    private String normalizarAccionesIa(String valor) {
        // CLASSIFY la usa el nodo MAPI de los workflows (Fase 2); sin ella en la
        // lista, la casilla de la pantalla se descartaba al guardar.
        Set<String> permitidas = Set.of("SUMMARY", "ANSWER", "CHAT", AiAssistantService.ACCION_CLASIFICAR);
        Set<String> salidas = new java.util.LinkedHashSet<>();
        for (String parte : valor.split(",")) {
            String limpia = parte.trim().toUpperCase();
            if (permitidas.contains(limpia)) {
                salidas.add(limpia);
            }
        }
        return salidas.isEmpty() ? "SUMMARY,ANSWER" : String.join(",", salidas);
    }

    /**
     * Normaliza los modelos Gemini deprecados de las series 1.x y 2.x al vigente por defecto.
     */
    private String normalizeGeminiModel(String provider, String model) {
        if (model == null) {
            return null;
        }
        String lower = model.toLowerCase();
        if ("GEMINI".equalsIgnoreCase(provider) && (lower.startsWith("gemini-1.") || lower.startsWith("gemini-2."))) {
            return "gemini-3.6-flash";
        }
        return model;
    }

private AiConfigResponse toAiResponse(AiConfig cfg) {
          return new AiConfigResponse(
                  cfg.getIdAiConfig(),
                  cfg.getEnabled(),
                  cfg.getProvider(),
                  cfg.getModel(),
                  // No se devuelve nada de la clave, ni enmascarado. Antes se
                  // mostraban los ultimos cuatro caracteres reales, lo que
                  // basta para identificar una clave filtrada o para confirmar
                  // cual esta puesta. El patron que ya usa la configuracion de
                  // Graph, un booleano, es el correcto.
                  cfg.getApiKey() != null && !cfg.getApiKey().isBlank(),
                  cfg.getBaseUrl(),
                  cfg.getProviderIcon(),
                  cfg.getRulesText() == null ? "" : cfg.getRulesText(),
                  cfg.getEnabledActions() == null ? "SUMMARY,ANSWER" : cfg.getEnabledActions(),
                  cfg.getDailyLimit(),
                  consultasIaHoy(),
                  Boolean.TRUE.equals(cfg.getRespondePrimero()));
      }

    /** Consultas a la IA de hoy (zona de Guayaquil), para mostrarlas junto al límite. */
    private Integer consultasIaHoy() {
        try {
            return jdbcTemplateIa == null ? null : jdbcTemplateIa.query(
                    "SELECT count_usage FROM ai_daily_usage WHERE day_usage = ?",
                    rs -> rs.next() ? rs.getInt(1) : 0,
                    java.time.LocalDate.now(java.time.ZoneId.of("America/Guayaquil")));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String maskKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }
        if (apiKey.length() <= 4) {
            return "****";
        }
        return "****" + apiKey.substring(apiKey.length() - 4);
    }

    /** Convierte cadenas vacías en null para no guardar iconos o colores en blanco. */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}