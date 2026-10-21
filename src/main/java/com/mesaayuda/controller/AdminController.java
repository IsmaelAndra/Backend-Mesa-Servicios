package com.mesaayuda.controller;

import com.mesaayuda.dto.AuditEventResponse;
import com.mesaayuda.dto.AiConfigRequest;
import com.mesaayuda.dto.AiConfigResponse;
import com.mesaayuda.dto.DailySummaryResponse;
import com.mesaayuda.dto.GraphAuditResponse;
import com.mesaayuda.dto.MicrosoftGraphConfigRequest;
import com.mesaayuda.dto.MicrosoftGraphConfigResponse;
import com.mesaayuda.dto.MonthlyEvaluationResponse;
import com.mesaayuda.dto.NotificationTemplateCreateRequest;
import com.mesaayuda.dto.NotificationTemplateUpdateRequest;
import com.mesaayuda.dto.RoutingConfigRequest;
import com.mesaayuda.dto.SupervisorEvaluationRequest;
import com.mesaayuda.dto.SystemAuditLogResponse;
import com.mesaayuda.entity.*;
import com.mesaayuda.service.AdminService;
import com.mesaayuda.service.DailySummaryService;
import com.mesaayuda.service.EvaluationService;
import com.mesaayuda.service.MicrosoftGraphConfigService;
import com.mesaayuda.service.RoutingConfigService;
import com.mesaayuda.service.SystemAuditQueryService;
import com.mesaayuda.service.SystemAuditService;
import com.mesaayuda.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.HashMap;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/admin")
@Tag(name = "Administración", description = "Panel de administración (ADMIN, SUPER_ADMIN)")
public class AdminController {

    @Autowired
    private AdminService adminService;

    @Autowired
    private EvaluationService evaluationService;

    @Autowired
    private UserService userService;

    @Autowired
    private SystemAuditService systemAuditService;

    @Autowired
    private SystemAuditQueryService systemAuditQueryService;

    @Autowired
    private MicrosoftGraphConfigService microsoftGraphConfigService;

    @Autowired
    private DailySummaryService dailySummaryService;

    @Autowired
    private RoutingConfigService routingConfigService;

    private boolean isAdminOrSuperAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) return false;
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_SUPER_ADMIN"));
    }

    private void checkAdminOrSuperAdmin() {
        if (!isAdminOrSuperAdmin()) {
            throw new org.springframework.security.access.AccessDeniedException("Se requiere rol ADMIN o SUPER_ADMIN");
        }
    }

    private boolean isSuperAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) return false;
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN"));
    }

    private void checkSuperAdmin() {
        if (!isSuperAdmin()) {
            throw new org.springframework.security.access.AccessDeniedException("Se requiere rol SUPER_ADMIN");
        }
    }

    // ============================ CONFIGURACIÓN IA ============================

    @GetMapping("/ai-config")
    @Operation(summary = "Obtener configuración IA")
    public ResponseEntity<AiConfigResponse> getAiConfig() {
        checkAdminOrSuperAdmin();
        return ResponseEntity.ok(adminService.getAiConfig());
    }

    @PutMapping("/ai-config")
    @Operation(summary = "Guardar configuración IA (SUPER_ADMIN)")
    public ResponseEntity<AiConfigResponse> updateAiConfig(@Valid @RequestBody AiConfigRequest request) {
        checkSuperAdmin();
        UUID userId = userService.getCurrentUser().getIdUser();
        AiConfigResponse saved = adminService.saveAiConfig(request, userId);
        systemAuditService.record("AI_CONFIG", "AiConfig", saved.id());
        return ResponseEntity.ok(saved);
    }

    // ============================ BRANDING ============================

    @GetMapping("/branding")
    @Operation(summary = "Obtener configuración de marca (logo y colores)")
    public ResponseEntity<Map<String, Object>> branding(HttpServletRequest request) {
        return ResponseEntity.ok(brandingResponse(adminService.getBranding(), request));
    }

    @PutMapping("/branding")
    @Operation(summary = "Actualizar colores y logo de la marca")
    public ResponseEntity<Map<String, Object>> updateBranding(@RequestBody BrandingConfig config,
                                                              HttpServletRequest request) {
        checkAdminOrSuperAdmin();
        return ResponseEntity.ok(brandingResponse(adminService.updateBranding(config), request));
    }

    @PostMapping("/branding/logo")
    @Operation(summary = "Subir logo del sistema")
    public ResponseEntity<Map<String, String>> uploadLogo(@RequestParam("file") MultipartFile file,
                                                          HttpServletRequest request) {
        checkAdminOrSuperAdmin();
        Map<String, String> result = adminService.saveLogo(file);
        result.put("url", toAbsoluteUrl(request, result.get("url")));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/branding/maintenance-logo")
    @Operation(summary = "Subir el logo de la pantalla de mantenimiento")
    public ResponseEntity<Map<String, String>> uploadMaintenanceLogo(@RequestParam("file") MultipartFile file,
                                                                     HttpServletRequest request) {
        checkSuperAdmin();
        Map<String, String> result = adminService.saveMaintenanceLogo(file);
        result.put("url", toAbsoluteUrl(request, result.get("url")));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/branding/background")
    @Operation(summary = "Subir imagen de fondo del sistema")
    public ResponseEntity<Map<String, String>> uploadBackground(@RequestParam("file") MultipartFile file,
                                                                HttpServletRequest request) {
        checkAdminOrSuperAdmin();
        Map<String, String> result = adminService.saveBackground(file);
        result.put("url", toAbsoluteUrl(request, result.get("url")));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/branding/logo-login")
    @Operation(summary = "Subir logo de la pantalla de login")
    public ResponseEntity<Map<String, String>> uploadLogoLogin(@RequestParam("file") MultipartFile file,
                                                               HttpServletRequest request) {
        checkAdminOrSuperAdmin();
        Map<String, String> result = adminService.saveLogoLogin(file);
        result.put("url", toAbsoluteUrl(request, result.get("url")));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/branding/system-icon")
    @Operation(summary = "Subir icono del sistema (favicon interno)")
    public ResponseEntity<Map<String, String>> uploadSystemIcon(@RequestParam("file") MultipartFile file,
                                                                 HttpServletRequest request) {
        checkAdminOrSuperAdmin();
        Map<String, String> result = adminService.saveSystemIcon(file);
        result.put("url", toAbsoluteUrl(request, result.get("url")));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/branding/loading-image")
    @Operation(summary = "Subir la imagen central de la pantalla de carga")
    public ResponseEntity<Map<String, String>> uploadLoadingImage(@RequestParam("file") MultipartFile file,
                                                                 HttpServletRequest request) {
        checkAdminOrSuperAdmin();
        Map<String, String> result = adminService.saveLoadingImage(file);
        result.put("url", toAbsoluteUrl(request, result.get("url")));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/branding/report-logo")
    @Operation(summary = "Subir el icono de la cabecera de los reportes PDF (si no hay, se usa el logo)")
    public ResponseEntity<Map<String, String>> uploadReportLogo(@RequestParam("file") MultipartFile file,
                                                                HttpServletRequest request) {
        checkAdminOrSuperAdmin();
        Map<String, String> result = adminService.saveReportLogo(file);
        result.put("url", toAbsoluteUrl(request, result.get("url")));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/branding/profile-cover")
    @Operation(summary = "Subir portada de la cabecera del perfil (para todos los usuarios)")
    public ResponseEntity<Map<String, String>> uploadProfileCover(@RequestParam("file") MultipartFile file,
                                                                  HttpServletRequest request) {
        checkAdminOrSuperAdmin();
        Map<String, String> result = adminService.saveProfileCover(file);
        result.put("url", toAbsoluteUrl(request, result.get("url")));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/branding/chat-background")
    @Operation(summary = "Subir imagen de fondo de los chats según el modo claro u oscuro")
    public ResponseEntity<Map<String, String>> uploadChatBackground(
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "mode", defaultValue = "light") String mode,
            HttpServletRequest request) {
        checkAdminOrSuperAdmin();
        Map<String, String> result = adminService.saveChatBackground(file, mode);
        result.put("url", toAbsoluteUrl(request, result.get("url")));
        return ResponseEntity.ok(result);
    }

    private Map<String, Object> brandingResponse(BrandingConfig config, HttpServletRequest request) {
        Map<String, Object> m = new HashMap<>();
        String chatBackgroundLightUrl = config.getChatBackgroundLightUrl();
        if ((chatBackgroundLightUrl == null || chatBackgroundLightUrl.isBlank())
                && config.getChatBackgroundUrl() != null && !config.getChatBackgroundUrl().isBlank()) {
            chatBackgroundLightUrl = config.getChatBackgroundUrl();
        }
        m.put("logoUrl", toAbsoluteUrl(request, config.getLogoUrl()));
        m.put("backgroundUrl", toAbsoluteUrl(request, config.getBackgroundUrl()));
        m.put("logoLoginUrl", toAbsoluteUrl(request, config.getLogoLoginUrl()));
        m.put("systemIconUrl", toAbsoluteUrl(request, config.getSystemIconUrl()));
        m.put("reportLogoUrl", toAbsoluteUrl(request, config.getReportLogoUrl()));
        m.put("profileCoverUrl", toAbsoluteUrl(request, config.getProfileCoverUrl()));
        m.put("chatBackgroundUrl", toAbsoluteUrl(request, config.getChatBackgroundUrl()));
        m.put("chatBackgroundLightUrl", toAbsoluteUrl(request, chatBackgroundLightUrl));
        m.put("chatBackgroundDarkUrl", toAbsoluteUrl(request, config.getChatBackgroundDarkUrl()));
        m.put("primaryColor", config.getPrimaryColor());
        m.put("secondaryColor", config.getSecondaryColor());
        m.put("maintenanceMode", Boolean.TRUE.equals(config.getMaintenanceMode()));
        m.put("maintenanceTitle", config.getMaintenanceTitle() == null ? "" : config.getMaintenanceTitle());
        m.put("maintenanceMessage", config.getMaintenanceMessage() == null ? "" : config.getMaintenanceMessage());
        m.put("maintenanceEta", config.getMaintenanceEta() == null ? "" : config.getMaintenanceEta());
        m.put("maintenanceLogoUrl", toAbsoluteUrl(request, config.getMaintenanceLogoUrl()));
        m.put("maintenanceIcon", config.getMaintenanceIcon() == null ? "" : config.getMaintenanceIcon());
        m.put("maintenanceIconColor", config.getMaintenanceIconColor() == null ? "" : config.getMaintenanceIconColor());
        // Pantalla de carga. Los textos vacios viajan como cadena para que el
        // formulario del administrador no los pierda al recargar.
        m.put("loadingMode", config.getLoadingMode() == null ? "AUTO" : config.getLoadingMode());
        m.put("loadingImageUrl", toAbsoluteUrl(request, config.getLoadingImageUrl()));
        m.put("loadingIcon", config.getLoadingIcon() == null ? "" : config.getLoadingIcon());
        m.put("loadingBackgroundColor", config.getLoadingBackgroundColor() == null ? "" : config.getLoadingBackgroundColor());
        m.put("loadingTextColor", config.getLoadingTextColor() == null ? "" : config.getLoadingTextColor());
        m.put("loadingTitle", config.getLoadingTitle() == null ? "" : config.getLoadingTitle());
        m.put("loadingMessage", config.getLoadingMessage() == null ? "" : config.getLoadingMessage());
        m.put("loadingAnimation", config.getLoadingAnimation() == null ? "SPINNER" : config.getLoadingAnimation());
        m.put("loadingLogoEffect", config.getLoadingLogoEffect() == null ? "NONE" : config.getLoadingLogoEffect());
        m.put("loadingProgress", Boolean.TRUE.equals(config.getLoadingProgress()));
        m.put("loadingTheme", config.getLoadingTheme() == null ? "LIGHT" : config.getLoadingTheme());
        m.put("loadingMinDurationMs", config.getLoadingMinDurationMs() == null ? 1200 : config.getLoadingMinDurationMs());
        m.put("loadingSoloMarca", Boolean.TRUE.equals(config.getLoadingSoloMarca()));
        m.put("loadingImageSize", config.getLoadingImageSize() == null ? 112 : config.getLoadingImageSize());
        return m;
    }

    /**
     * En producción la API y la aplicación comparten origen (Nginx del frontend),
     * así que basta una ruta relativa ("/api/uploads/..."). Armar la URL completa
     * dependía de que TODOS los proxies pasaran bien X-Forwarded-Proto y el host:
     * si el del servidor no lo hacía, salía http://dominio:80/... dentro de una
     * página https y el navegador no mostraba la imagen.
     */
    @org.springframework.beans.factory.annotation.Value("${app.urls-relativas:false}")
    private boolean urlsRelativas;

    private String toAbsoluteUrl(HttpServletRequest request, String path) {
        if (path == null || path.startsWith("http")) return path == null ? "" : path;
        if (urlsRelativas) return request.getContextPath() + path;
        String base = request.getScheme() + "://" + request.getServerName()
                + ":" + request.getServerPort() + request.getContextPath();
        return base + path;
    }

    // ============================ CATÁLOGOS ============================

    @GetMapping("/catalog")
    @Operation(summary = "Obtener catálogos del sistema (áreas, equipos, categorías, SLA)")
    public ResponseEntity<Map<String, Object>> catalog(HttpServletRequest request) {
        // Es público porque el registro necesita las áreas antes de tener sesión.
        // Sin sesión solo se entregan áreas y sedes: roles, equipos, categorías
        // (con sus descripciones) y demás son información interna.
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        boolean conSesion = auth != null && auth.isAuthenticated()
                && !(auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken);
        String cabecera = request.getHeader("Authorization");
        if (!conSesion && cabecera != null && cabecera.startsWith("Bearer ")) {
            // Trae token pero caducado: 401 para que el frontend renueve la sesión
            // y vuelva a pedir el catálogo completo, en vez de quedarse con el
            // recortado.
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).build();
        }
        Map<String, Object> catalogo = adminService.getCatalog();
        if (!conSesion) {
            Map<String, Object> publico = new java.util.LinkedHashMap<>();
            publico.put("areas", catalogo.getOrDefault("areas", List.of()));
            publico.put("sucursales", catalogo.getOrDefault("sucursales", List.of()));
            return ResponseEntity.ok(publico);
        }
        return ResponseEntity.ok(catalogo);
    }

    // ============================ ÁREAS ============================

    @GetMapping("/areas")
    public ResponseEntity<List<Area>> areas() {
        checkAdminOrSuperAdmin();
        return ResponseEntity.ok(adminService.getAreas());
    }

    @PostMapping("/areas")
    public ResponseEntity<Area> createArea(@RequestBody Map<String, Object> body) {
        checkAdminOrSuperAdmin();
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.saveArea(
                null, value(body, "name"), value(body, "description"),
                toInt(body.get("priorityLevel")), toBool(body.get("status")),
                value(body, "icon"), value(body, "color")));
    }

    @PutMapping("/areas/{id}")
    public ResponseEntity<Area> updateArea(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        checkAdminOrSuperAdmin();
        return ResponseEntity.ok(adminService.saveArea(
                id, value(body, "name"), value(body, "description"),
                toInt(body.get("priorityLevel")), toBool(body.get("status")),
                value(body, "icon"), value(body, "color")));
    }

    @DeleteMapping("/areas/{id}")
    public ResponseEntity<Void> deleteArea(@PathVariable UUID id) {
        checkAdminOrSuperAdmin();
        adminService.deleteArea(id);
        return ResponseEntity.noContent().build();
    }

    // ============================ SUCURSALES ============================

    @GetMapping("/sucursales")
    @Operation(summary = "Listar las sedes de la institución")
    public ResponseEntity<List<Sucursal>> sucursales() {
        checkSuperAdmin();
        return ResponseEntity.ok(adminService.getSucursales());
    }

    @PostMapping("/sucursales")
    @Operation(summary = "Crear una sede")
    public ResponseEntity<Sucursal> createSucursal(@RequestBody Map<String, Object> body) {
        checkSuperAdmin();
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.saveSucursal(
                null, value(body, "code"), value(body, "name"), value(body, "description"),
                value(body, "address"), value(body, "phone"),
                value(body, "icon"), value(body, "color"), toBool(body.get("status"))));
    }

    @PutMapping("/sucursales/{id}")
    @Operation(summary = "Editar una sede")
    public ResponseEntity<Sucursal> updateSucursal(@PathVariable UUID id,
                                                   @RequestBody Map<String, Object> body) {
        checkSuperAdmin();
        return ResponseEntity.ok(adminService.saveSucursal(
                id, value(body, "code"), value(body, "name"), value(body, "description"),
                value(body, "address"), value(body, "phone"),
                value(body, "icon"), value(body, "color"), toBool(body.get("status"))));
    }

    @DeleteMapping("/sucursales/{id}")
    @Operation(summary = "Eliminar una sede")
    public ResponseEntity<Void> deleteSucursal(@PathVariable UUID id) {
        checkSuperAdmin();
        adminService.deleteSucursal(id);
        return ResponseEntity.noContent().build();
    }

    // ============================ CATEGORÍAS ============================

    @GetMapping("/categories")
    public ResponseEntity<List<Map<String, Object>>> categories() {
        checkAdminOrSuperAdmin();
        return ResponseEntity.ok(adminService.getCategoriesForAdmin());
    }

    @PostMapping("/categories")
    public ResponseEntity<Category> createCategory(@RequestBody Map<String, Object> body) {
        checkAdminOrSuperAdmin();
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.saveCategory(null,
                value(body, "name"), value(body, "description"),
                toUuid(body.get("teamId")), value(body, "icon"), value(body, "color")));
    }

    @PutMapping("/categories/{id}")
    public ResponseEntity<Category> updateCategory(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        checkAdminOrSuperAdmin();
        return ResponseEntity.ok(adminService.saveCategory(id,
                value(body, "name"), value(body, "description"),
                toUuid(body.get("teamId")), value(body, "icon"), value(body, "color")));
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable UUID id) {
        checkAdminOrSuperAdmin();
        adminService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }

    // ============================ TIPOS DE TICKET ============================

    @GetMapping("/ticket-types")
    public ResponseEntity<List<Map<String, Object>>> ticketTypes() {
        checkAdminOrSuperAdmin();
        return ResponseEntity.ok(adminService.getTicketTypes());
    }

    @PostMapping("/ticket-types")
    public ResponseEntity<Map<String, Object>> createTicketType(@RequestBody Map<String, Object> body) {
        checkAdminOrSuperAdmin();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminService.saveTicketType(value(body, "name"), value(body, "description"),
                        value(body, "icon"), value(body, "color")));
    }

    @PutMapping("/ticket-types/{id}")
    public ResponseEntity<Map<String, Object>> updateTicketType(@PathVariable UUID id,
                                                                @RequestBody Map<String, Object> body) {
        checkAdminOrSuperAdmin();
        return ResponseEntity.ok(adminService.updateTicketType(
                id, value(body, "name"), value(body, "description"),
                value(body, "icon"), value(body, "color")));
    }

    @DeleteMapping("/ticket-types/{id}")
    public ResponseEntity<Void> deleteTicketType(@PathVariable UUID id) {
        checkAdminOrSuperAdmin();
        adminService.deleteTicketType(id);
        return ResponseEntity.noContent().build();
    }

    // ============================ EQUIPOS ============================

    @GetMapping("/teams")
    public ResponseEntity<List<SupportTeam>> teams() {
        checkAdminOrSuperAdmin();
        return ResponseEntity.ok(adminService.getTeams());
    }

    @PostMapping("/teams")
    public ResponseEntity<SupportTeam> createTeam(@RequestBody Map<String, Object> body) {
        checkAdminOrSuperAdmin();
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.saveTeam(null,
                value(body, "name"), value(body, "description"),
                value(body, "method"), toBool(body.get("active")),
                value(body, "icon"), value(body, "color")));
    }

    @PutMapping("/teams/{id}")
    public ResponseEntity<SupportTeam> updateTeam(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        checkAdminOrSuperAdmin();
        return ResponseEntity.ok(adminService.saveTeam(id,
                value(body, "name"), value(body, "description"),
                value(body, "method"), toBool(body.get("active")),
                value(body, "icon"), value(body, "color")));
    }

    @PostMapping("/teams/{id}/members/{userId}")
    public ResponseEntity<Void> addMember(@PathVariable UUID id, @PathVariable UUID userId) {
        checkAdminOrSuperAdmin();
        adminService.addMemberToTeam(id, userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/teams/{id}/members/{userId}")
    public ResponseEntity<Void> removeMember(@PathVariable UUID id, @PathVariable UUID userId) {
        checkAdminOrSuperAdmin();
        adminService.removeMemberFromTeam(id, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/teams/{id}/members")
    public ResponseEntity<List<TeamUser>> teamMembers(@PathVariable UUID id) {
        checkAdminOrSuperAdmin();
        return ResponseEntity.ok(adminService.teamMembers(id));
    }

    // ============================ ENRUTAMIENTO (SUPER_ADMIN) ============================

    @GetMapping("/routing-config")
    @Operation(summary = "Obtener la configuración global de asignación automática")
    public ResponseEntity<RoutingConfig> getRoutingConfig() {
        checkAdminOrSuperAdmin();
        return ResponseEntity.ok(routingConfigService.getConfig());
    }

    @PutMapping("/routing-config")
    @Operation(summary = "Activar o desactivar la asignación automática de tickets (SUPER_ADMIN)")
    public ResponseEntity<RoutingConfig> updateRoutingConfig(@Valid @RequestBody RoutingConfigRequest request) {
        checkSuperAdmin();
        RoutingConfig saved = routingConfigService.update(request);
        systemAuditService.record("ROUTING_CONFIG", "RoutingConfig", com.mesaayuda.entity.IdsConfiguracion.ROUTING);
        return ResponseEntity.ok(saved);
    }

    // ============================ SLA ============================

    @GetMapping("/sla/policies")
    public ResponseEntity<List<SlaPolicy>> slaPolicies() {
        checkAdminOrSuperAdmin();
        return ResponseEntity.ok(adminService.getSlaPolicies());
    }

    @GetMapping("/sla/rules")
    public ResponseEntity<List<Map<String, Object>>> slaRules() {
        checkAdminOrSuperAdmin();
        return ResponseEntity.ok(adminService.getSlaRulesForAdmin());
    }

    @PostMapping("/sla/policies")
    public ResponseEntity<SlaPolicy> createPolicy(@RequestBody Map<String, Object> body) {
        checkAdminOrSuperAdmin();
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.saveSlaPolicy(null,
                value(body, "name"), value(body, "description"),
                toInt(body.get("responseMinutes")), toInt(body.get("resolutionMinutes")),
                toBool(body.get("active")), value(body, "icon"), value(body, "color")));
    }

    @PutMapping("/sla/policies/{id}")
    public ResponseEntity<SlaPolicy> updatePolicy(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        checkAdminOrSuperAdmin();
        return ResponseEntity.ok(adminService.saveSlaPolicy(id,
                value(body, "name"), value(body, "description"),
                toInt(body.get("responseMinutes")), toInt(body.get("resolutionMinutes")),
                toBool(body.get("active")), value(body, "icon"), value(body, "color")));
    }

    @DeleteMapping("/sla/policies/{id}")
    @Operation(summary = "Desactivar una política SLA")
    public ResponseEntity<Void> deletePolicy(@PathVariable UUID id) {
        checkAdminOrSuperAdmin();
        adminService.deleteSlaPolicy(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sla/rules")
    public ResponseEntity<SlaRule> createRule(@RequestBody Map<String, Object> body) {
        checkAdminOrSuperAdmin();
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.saveSlaRule(null,
                toUuid(body.get("policyId")), toUuid(body.get("areaId")),
                toUuid(body.get("priorityId")), toUuid(body.get("categoryId")),
                toUuid(body.get("typeId")), toBool(body.get("active"))));
    }

    @PutMapping("/sla/rules/{id}")
    @Operation(summary = "Editar una regla SLA")
    public ResponseEntity<SlaRule> updateRule(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        checkAdminOrSuperAdmin();
        return ResponseEntity.ok(adminService.saveSlaRule(id,
                toUuid(body.get("policyId")), toUuid(body.get("areaId")),
                toUuid(body.get("priorityId")), toUuid(body.get("categoryId")),
                toUuid(body.get("typeId")), toBool(body.get("active"))));
    }

    @DeleteMapping("/sla/rules/{id}")
    @Operation(summary = "Eliminar una regla SLA")
    public ResponseEntity<Void> deleteRule(@PathVariable UUID id) {
        checkAdminOrSuperAdmin();
        adminService.deleteSlaRule(id);
        return ResponseEntity.noContent().build();
    }

    // ============================ ESCALAMIENTO ============================

    @GetMapping("/sla/escalations")
    public ResponseEntity<List<Map<String, Object>>> slaEscalations() {
        checkAdminOrSuperAdmin();
        return ResponseEntity.ok(adminService.getEscalationsForAdmin());
    }

    @PostMapping("/sla/escalations")
    public ResponseEntity<SlaEscalation> createEscalation(@RequestBody Map<String, Object> body) {
        checkAdminOrSuperAdmin();
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.saveEscalation(null,
                toUuid(body.get("policyId")), toInt(body.get("trigger")), value(body, "action"),
                toUuid(body.get("teamId")), toUuid(body.get("userId")), toUuid(body.get("templateId"))));
    }

    @PutMapping("/sla/escalations/{id}")
    @Operation(summary = "Editar una regla de escalamiento")
    public ResponseEntity<SlaEscalation> updateEscalation(@PathVariable UUID id,
                                                          @RequestBody Map<String, Object> body) {
        checkAdminOrSuperAdmin();
        return ResponseEntity.ok(adminService.saveEscalation(id,
                toUuid(body.get("policyId")), toInt(body.get("trigger")), value(body, "action"),
                toUuid(body.get("teamId")), toUuid(body.get("userId")), toUuid(body.get("templateId"))));
    }

    @DeleteMapping("/sla/escalations/{id}")
    @Operation(summary = "Eliminar una regla de escalamiento")
    public ResponseEntity<Void> deleteEscalation(@PathVariable UUID id) {
        checkAdminOrSuperAdmin();
        adminService.deleteEscalation(id);
        return ResponseEntity.noContent().build();
    }

    // ============================ PLANTILLAS ============================

    @GetMapping("/templates")
    public ResponseEntity<List<NotificationTemplate>> templates() {
        checkAdminOrSuperAdmin();
        return ResponseEntity.ok(adminService.getTemplates());
    }

    @PutMapping("/templates/{id}")
    @Operation(summary = "Editar plantilla de notificación, icono y regla de destinatario")
    public ResponseEntity<NotificationTemplate> updateTemplate(
            @PathVariable UUID id, @Valid @RequestBody NotificationTemplateUpdateRequest request) {
        checkAdminOrSuperAdmin();
        NotificationTemplate saved = adminService.updateTemplate(
                id, request.subjectTemplate(), request.bodyTemplate(), request.activeTemplate(),
                request.recipientRule(), request.recipientEmail(),
                request.iconTemplate(), request.colorTemplate(), request.imageUrl());
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/templates")
    @Operation(summary = "Crear o sobrescribir la plantilla de un evento")
    public ResponseEntity<NotificationTemplate> createTemplate(
            @Valid @RequestBody NotificationTemplateCreateRequest request) {
        checkAdminOrSuperAdmin();
        NotificationTemplate saved = adminService.saveTemplate(null,
                request.nameTemplate(), request.typeTemplate(),
                request.subjectTemplate(), request.bodyTemplate(), request.activeTemplate(),
                request.recipientRule(), request.recipientEmail(),
                request.iconTemplate(), request.colorTemplate(), request.imageUrl());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PostMapping("/templates/image")
    @Operation(summary = "Subir la imagen que se puede usar en el HTML de una plantilla")
    public ResponseEntity<Map<String, String>> uploadTemplateImage(@RequestParam("file") MultipartFile file,
                                                                   HttpServletRequest request) {
        checkAdminOrSuperAdmin();
        Map<String, String> result = adminService.saveTemplateImage(file);
        result.put("url", toAbsoluteUrl(request, result.get("url")));
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/templates/{id}")
    @Operation(summary = "Eliminar una plantilla de notificación")
    public ResponseEntity<Void> deleteTemplate(@PathVariable UUID id) {
        checkAdminOrSuperAdmin();
        adminService.deleteTemplate(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<List<AuditEventResponse>> auditLogs() {
        checkAdminOrSuperAdmin();
        return ResponseEntity.ok(adminService.getAuditEvents());
    }

  /**
     * Auditoría del sistema: accesos denegados, cambios de contraseña, intentos
     * fallidos, altas y cambios del doble factor.
     *
     * <p>Solo el super administrador. No por prudencia genérica, sino por lo que
     * contiene: estas filas dicen qué usuario intentó qué y desde qué dirección.
     * Un ADMIN con acceso vería el rastro de intentos de los demás, incluida la
     * actividad de los administradores que superiores.
     */
  @GetMapping("/audit-sistema")
    @Operation(summary = "Consultar la auditoría del sistema (SUPER_ADMIN)")
    public ResponseEntity<List<SystemAuditLogResponse>> auditoriaSistema(
          @RequestParam(required = false) String accion,
          @RequestParam(required = false) String usuario,
          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          OffsetDateTime desde,
          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          OffsetDateTime hasta,
          @RequestParam(defaultValue = "0") int pagina,
          @RequestParam(defaultValue = "100") int tamano) {
        checkSuperAdmin();
        return ResponseEntity.ok(
                systemAuditQueryService.consultar(accion, usuario, desde, hasta, pagina, tamano));
    }

  /** Acciones registradas, para el desplegable de filtro. */
  @GetMapping("/audit-sistema/acciones")
    @Operation(summary = "Listar las acciones de auditoría disponibles (SUPER_ADMIN)")
    public ResponseEntity<List<String>> accionesAuditoria() {
        checkSuperAdmin();
        return ResponseEntity.ok(systemAuditQueryService.acciones());
    }

    // ============================ MICROSOFT GRAPH ============================

    @GetMapping("/microsoft-graph")
    @Operation(summary = "Obtener configuración de Microsoft Graph (sin el client_secret)")
    public ResponseEntity<MicrosoftGraphConfigResponse> getMicrosoftGraphConfig() {
        checkAdminOrSuperAdmin();
        return ResponseEntity.ok(microsoftGraphConfigService.getConfig());
    }

    @PutMapping("/microsoft-graph")
    @Operation(summary = "Guardar configuración de Microsoft Graph (SUPER_ADMIN)")
    public ResponseEntity<MicrosoftGraphConfigResponse> updateMicrosoftGraphConfig(
            @Valid @RequestBody MicrosoftGraphConfigRequest request, HttpServletRequest httpRequest) {
        checkSuperAdmin();
        User current = userService.getCurrentUser();
        MicrosoftGraphConfigResponse saved = microsoftGraphConfigService.save(
                request, current.getIdUser(), current.getCorporateEmailUser(),
                httpRequest.getRemoteAddr());
        systemAuditService.record("MSGRAPH_CONFIG", "MicrosoftGraphConfig", saved.id());
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/microsoft-graph/test")
    @Operation(summary = "Probar la conexión con Microsoft Graph (SUPER_ADMIN)")
    public ResponseEntity<Map<String, Object>> testMicrosoftGraph() {
        checkSuperAdmin();
        return ResponseEntity.ok(microsoftGraphConfigService.testConnection());
    }

    @PostMapping("/microsoft-graph/email-test")
    @Operation(summary = "Enviar correo de prueba vía sendMail de Microsoft Graph (SUPER_ADMIN)")
    public ResponseEntity<Map<String, Object>> testMicrosoftGraphEmail(@RequestBody(required = false) Map<String, Object> body) {
        checkSuperAdmin();
        String to = body != null ? value(body, "to") : null;
        return ResponseEntity.ok(microsoftGraphConfigService.testEmail(to));
    }

    @GetMapping("/microsoft-graph/audit")
    @Operation(summary = "Auditoría de cambios de la configuración de Microsoft Graph (SUPER_ADMIN)")
    public ResponseEntity<List<GraphAuditResponse>> microsoftGraphAudit() {
        checkSuperAdmin();
        return ResponseEntity.ok(microsoftGraphConfigService.auditList());
    }

    // ============================ RESUMEN DIARIO ============================

    @PostMapping("/daily-summary/send")
    @Operation(summary = "Enviar el resumen diario por correo ahora (ADMIN/SUPER_ADMIN)")
    public ResponseEntity<DailySummaryResponse> sendDailySummary(
            @RequestBody(required = false) Map<String, Object> body) {
        checkAdminOrSuperAdmin();
        String extra = body != null ? value(body, "to") : null;
        DailySummaryResponse result = dailySummaryService.send(extra);
        systemAuditService.record("DAILY_SUMMARY", "DailySummary", null);
        return ResponseEntity.ok(result);
    }

    // ============================ EVALUACIONES ============================

    @GetMapping("/evaluations")
    @Operation(summary = "Evaluaciones de un mes")
    public ResponseEntity<List<MonthlyEvaluationResponse>> evaluationsByMonth(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate month) {
        checkAdminOrSuperAdmin();
        return ResponseEntity.ok(evaluationService.findByMonth(month));
    }

    @PostMapping("/evaluations/generate")
    @Operation(summary = "Generar evaluaciones mensuales de desempeño")
    public ResponseEntity<List<MonthlyEvaluationResponse>> generate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate month) {
        checkAdminOrSuperAdmin();
        return ResponseEntity.ok(evaluationService.generateAllForMonth(month));
    }

    @PutMapping("/evaluations/{id}/review")
    @Operation(summary = "Revisar evaluación de un agente")
    public ResponseEntity<MonthlyEvaluationResponse> review(
            @PathVariable UUID id, @Valid @RequestBody SupervisorEvaluationRequest request) {
        checkAdminOrSuperAdmin();
        return ResponseEntity.ok(evaluationService.review(id, request,
                userService.getCurrentUser().getIdUser()));
    }

    // ============================ HELPERS ============================

    private String value(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null ? null : v.toString();
    }

    private UUID toUuid(Object v) {
        return v == null ? null : UUID.fromString(v.toString());
    }

    private Integer toInt(Object v) {
        return v == null ? null : Integer.valueOf(v.toString());
    }

    private Boolean toBool(Object v) {
        return v == null ? null : Boolean.valueOf(v.toString());
    }
}