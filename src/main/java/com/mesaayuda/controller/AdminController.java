package com.mesaayuda.controller;

import com.mesaayuda.dto.AuditEventResponse;
import com.mesaayuda.dto.AiConfigRequest;
import com.mesaayuda.dto.AiConfigResponse;
import com.mesaayuda.dto.MonthlyEvaluationResponse;
import com.mesaayuda.dto.SupervisorEvaluationRequest;
import com.mesaayuda.entity.*;
import com.mesaayuda.service.AdminService;
import com.mesaayuda.service.EvaluationService;
import com.mesaayuda.service.SmtpService;
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
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin")
@Tag(name = "Administración", description = "Panel de administración (ADMIN, SUPER_ADMIN)")
public class AdminController {

    @Autowired
    private AdminService adminService;

    @Autowired
    private EvaluationService evaluationService;

    @Autowired
    private SmtpService smtpService;

    @Autowired
    private UserService userService;

    @Autowired
    private SystemAuditService systemAuditService;

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
        Long userId = userService.getCurrentUser().getIdUser();
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

    @PostMapping("/branding/background")
    @Operation(summary = "Subir imagen de fondo del sistema")
    public ResponseEntity<Map<String, String>> uploadBackground(@RequestParam("file") MultipartFile file,
                                                                HttpServletRequest request) {
        checkAdminOrSuperAdmin();
        Map<String, String> result = adminService.saveBackground(file);
        result.put("url", toAbsoluteUrl(request, result.get("url")));
        return ResponseEntity.ok(result);
    }

    private Map<String, Object> brandingResponse(BrandingConfig config, HttpServletRequest request) {
        Map<String, Object> m = new HashMap<>();
        m.put("logoUrl", toAbsoluteUrl(request, config.getLogoUrl()));
        m.put("backgroundUrl", toAbsoluteUrl(request, config.getBackgroundUrl()));
        m.put("primaryColor", config.getPrimaryColor());
        m.put("secondaryColor", config.getSecondaryColor());
        return m;
    }

    private String toAbsoluteUrl(HttpServletRequest request, String path) {
        if (path == null || path.startsWith("http")) return path == null ? "" : path;
        String base = request.getScheme() + "://" + request.getServerName()
                + ":" + request.getServerPort() + request.getContextPath();
        return base + path;
    }

    // ============================ CATÁLOGOS ============================

    @GetMapping("/catalog")
    @Operation(summary = "Obtener catálogos del sistema (áreas, equipos, categorías, SLA)")
    public ResponseEntity<Map<String, Object>> catalog() {
        return ResponseEntity.ok(adminService.getCatalog());
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
                toInt(body.get("priorityLevel")), toBool(body.get("status"))));
    }

    @PutMapping("/areas/{id}")
    public ResponseEntity<Area> updateArea(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        checkAdminOrSuperAdmin();
        return ResponseEntity.ok(adminService.saveArea(
                id, value(body, "name"), value(body, "description"),
                toInt(body.get("priorityLevel")), toBool(body.get("status"))));
    }

    @DeleteMapping("/areas/{id}")
    public ResponseEntity<Void> deleteArea(@PathVariable Long id) {
        checkAdminOrSuperAdmin();
        adminService.deleteArea(id);
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
                toLong(body.get("teamId")), value(body, "icon"), value(body, "color")));
    }

    @PutMapping("/categories/{id}")
    public ResponseEntity<Category> updateCategory(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        checkAdminOrSuperAdmin();
        return ResponseEntity.ok(adminService.saveCategory(id,
                value(body, "name"), value(body, "description"),
                toLong(body.get("teamId")), value(body, "icon"), value(body, "color")));
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
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
                .body(adminService.saveTicketType(value(body, "name"), value(body, "description")));
    }

    @PutMapping("/ticket-types/{id}")
    public ResponseEntity<Map<String, Object>> updateTicketType(@PathVariable Long id,
                                                                @RequestBody Map<String, Object> body) {
        checkAdminOrSuperAdmin();
        return ResponseEntity.ok(adminService.updateTicketType(
                id, value(body, "name"), value(body, "description")));
    }

    @DeleteMapping("/ticket-types/{id}")
    public ResponseEntity<Void> deleteTicketType(@PathVariable Long id) {
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
                value(body, "method"), toBool(body.get("active"))));
    }

    @PutMapping("/teams/{id}")
    public ResponseEntity<SupportTeam> updateTeam(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        checkAdminOrSuperAdmin();
        return ResponseEntity.ok(adminService.saveTeam(id,
                value(body, "name"), value(body, "description"),
                value(body, "method"), toBool(body.get("active"))));
    }

    @PostMapping("/teams/{id}/members/{userId}")
    public ResponseEntity<Void> addMember(@PathVariable Long id, @PathVariable Long userId) {
        checkAdminOrSuperAdmin();
        adminService.addMemberToTeam(id, userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/teams/{id}/members/{userId}")
    public ResponseEntity<Void> removeMember(@PathVariable Long id, @PathVariable Long userId) {
        checkAdminOrSuperAdmin();
        adminService.removeMemberFromTeam(id, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/teams/{id}/members")
    public ResponseEntity<List<TeamUser>> teamMembers(@PathVariable Long id) {
        checkAdminOrSuperAdmin();
        return ResponseEntity.ok(adminService.teamMembers(id));
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
                toBool(body.get("active"))));
    }

    @PutMapping("/sla/policies/{id}")
    public ResponseEntity<SlaPolicy> updatePolicy(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        checkAdminOrSuperAdmin();
        return ResponseEntity.ok(adminService.saveSlaPolicy(id,
                value(body, "name"), value(body, "description"),
                toInt(body.get("responseMinutes")), toInt(body.get("resolutionMinutes")),
                toBool(body.get("active"))));
    }

    @PostMapping("/sla/rules")
    public ResponseEntity<SlaRule> createRule(@RequestBody Map<String, Object> body) {
        checkAdminOrSuperAdmin();
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.saveSlaRule(null,
                toLong(body.get("policyId")), toLong(body.get("areaId")),
                toLong(body.get("priorityId")), toLong(body.get("categoryId")),
                toLong(body.get("typeId")), toBool(body.get("active"))));
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
                toLong(body.get("policyId")), toInt(body.get("trigger")), value(body, "action"),
                toLong(body.get("teamId")), toLong(body.get("userId")), toLong(body.get("templateId"))));
    }

    // ============================ PLANTILLAS ============================

    @GetMapping("/templates")
    public ResponseEntity<List<NotificationTemplate>> templates() {
        checkAdminOrSuperAdmin();
        return ResponseEntity.ok(adminService.getTemplates());
    }

    // ============================ CONFIGURACIÓN SMTP ============================

    @GetMapping("/smtp")
    public ResponseEntity<?> getSmtp() {
        checkAdminOrSuperAdmin();
        SmtpConfig cfg = smtpService.getConfig();
        SmtpConfig safe = new SmtpConfig();
        safe.setIdSmtp(cfg.getIdSmtp());
        safe.setHostSmtp(cfg.getHostSmtp());
        safe.setPortSmtp(cfg.getPortSmtp());
        safe.setUsernameSmtp(cfg.getUsernameSmtp());
        safe.setFromAddress(cfg.getFromAddress());
        safe.setDisplayName(cfg.getDisplayName());
        safe.setUseTls(cfg.getUseTls());
        safe.setEnabledSmtp(cfg.getEnabledSmtp());
        return ResponseEntity.ok(safe);
    }

    @PutMapping("/smtp")
    public ResponseEntity<SmtpConfig> updateSmtp(@RequestBody SmtpConfig config) {
        checkAdminOrSuperAdmin();
        SmtpConfig saved = smtpService.save(config);
        systemAuditService.record("SMTP_CONFIG", "SmtpConfig", saved.getIdSmtp());
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<List<AuditEventResponse>> auditLogs() {
        checkAdminOrSuperAdmin();
        return ResponseEntity.ok(adminService.getAuditEvents());
    }

    @PostMapping("/smtp/test")
    public ResponseEntity<Map<String, Object>> testSmtp(@RequestBody Map<String, Object> body) {
        checkAdminOrSuperAdmin();
        return ResponseEntity.ok(smtpService.testSend(
                value(body, "to"), value(body, "host"),
                toInt(body.get("port")), value(body, "username"),
                value(body, "password"), value(body, "from"),
                toBool(body.get("useTls") != null ? body.get("useTls") : Boolean.TRUE)));
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
            @PathVariable Long id, @Valid @RequestBody SupervisorEvaluationRequest request) {
        checkAdminOrSuperAdmin();
        return ResponseEntity.ok(evaluationService.review(id, request,
                userService.getCurrentUser().getIdUser()));
    }

    // ============================ HELPERS ============================

    private String value(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null ? null : v.toString();
    }

    private Long toLong(Object v) {
        return v == null ? null : Long.valueOf(v.toString());
    }

    private Integer toInt(Object v) {
        return v == null ? null : Integer.valueOf(v.toString());
    }

    private Boolean toBool(Object v) {
        return v == null ? null : Boolean.valueOf(v.toString());
    }
}