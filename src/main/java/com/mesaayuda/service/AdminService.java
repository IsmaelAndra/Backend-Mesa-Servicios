package com.mesaayuda.service;

import com.mesaayuda.entity.*;
import com.mesaayuda.exception.BadRequestException;
import com.mesaayuda.exception.ResourceNotFoundException;
import com.mesaayuda.repository.*;
import com.mesaayuda.dto.AuditEventResponse;
import com.mesaayuda.dto.AiConfigRequest;
import com.mesaayuda.dto.AiConfigResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class AdminService {

    @Autowired private RoleRepository roleRepository;
    @Autowired private AreaRepository areaRepository;
    @Autowired private StatusRepository statusRepository;
    @Autowired private PriorityRepository priorityRepository;
    @Autowired private SupportTeamRepository teamRepository;
    @Autowired private TeamUserRepository teamUserRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private SlaPolicyRepository slaPolicyRepository;
    @Autowired private SlaRuleRepository slaRuleRepository;
    @Autowired private SlaEscalationRepository escalationRepository;
    @Autowired private NotificationTemplateRepository templateRepository;
    @Autowired private TicketTypeRepository ticketTypeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private BrandingConfigRepository brandingConfigRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private com.mesaayuda.repository.AiConfigRepository aiConfigRepository;

    @Value("${app.uploads.dir}")
    private String uploadDir;

    @Transactional(readOnly = true)
    public Map<String, Object> getCatalog() {
        return Map.of(
                "roles", roleRepository.findAll().stream().map(Role::getNameRole).toList(),
                "areas", areaRepository.findByDeletedAreaFalseOrderByPriorityLevelDesc().stream()
                        .map(a -> Map.of("id", a.getIdArea(), "name", a.getNameArea(),
                                "priorityLevel", a.getPriorityLevel(), "status", a.getStatusArea())).toList(),
                "statuses", statusRepository.findAll().stream().map(Status::getNameStatus).toList(),
                "priorities", priorityRepository.findAllActive().stream()
                        .map(p -> Map.of("id", p.getIdPriority(), "name", p.getNamePriority(),
                                "level", p.getLevelPriority(), "color", p.getColorPriority())).toList(),
                "teams", teamRepository.findByActiveTeamTrue().stream()
                        .map(t -> Map.of("id", t.getIdTeam(), "name", t.getNameTeam(),
                                "method", t.getAssignmentMethod())).toList(),
                "categories", categoryRepository.findByDeletedCategoryFalse().stream()
                        .map(c -> Map.of("id", c.getIdCategory(), "name", c.getNameCategory(),
                                "teamId", c.getTeamCategory() != null ? c.getTeamCategory().getIdTeam() : null)).toList(),
                "ticketTypes", ticketTypeRepository.findByDeletedTicketTypeFalseOrderByNameTicketTypeAsc().stream()
                        .map(t -> Map.of("id", t.getIdTicketType(), "name", t.getNameTicketType(),
                                "description", t.getDescriptionTicketType())).toList(),
                "slaPolicies", slaPolicyRepository.findByActiveSlaPolicyTrue().stream()
                        .map(s -> Map.of("id", s.getIdSlaPolicy(), "name", s.getNameSlaPolicy(),
                                "response", s.getResponseTimeMinutes(), "resolution", s.getResolutionTimeMinutes())).toList()
        );
    }

    // ============================ TIPOS DE TICKET ============================

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTicketTypes() {
        return ticketTypeRepository.findByDeletedTicketTypeFalseOrderByNameTicketTypeAsc().stream()
                .map(t -> Map.<String, Object>of(
                        "id", t.getIdTicketType(),
                        "name", t.getNameTicketType(),
                        "description", t.getDescriptionTicketType() == null ? "" : t.getDescriptionTicketType()))
                .toList();
    }

    @Transactional
    public Map<String, Object> saveTicketType(String name, String description) {
        if (name == null || name.isBlank()) {
            throw new BadRequestException("El nombre del tipo es obligatorio");
        }
        if (ticketTypeRepository.existsByNameTicketType(name.trim())) {
            throw new BadRequestException("Ya existe un tipo con ese nombre");
        }
        TicketType t = new TicketType();
        t.setNameTicketType(name.trim().toUpperCase());
        t.setDescriptionTicketType(description == null ? null : description.trim());
        t.setDeletedTicketType(false);
        t = ticketTypeRepository.save(t);
        return Map.of("id", t.getIdTicketType(), "name", t.getNameTicketType());
    }

    @Transactional
    public Map<String, Object> updateTicketType(Long id, String name, String description) {
        TicketType t = ticketTypeRepository.findById(id)
                .filter(x -> !x.getDeletedTicketType())
                .orElseThrow(() -> new ResourceNotFoundException("Tipo de ticket", "id", id));
        if (name != null && !name.isBlank()) {
            t.setNameTicketType(name.trim().toUpperCase());
        }
        if (description != null) {
            t.setDescriptionTicketType(description.trim());
        }
        t = ticketTypeRepository.save(t);
        return Map.of("id", t.getIdTicketType(), "name", t.getNameTicketType());
    }

    @Transactional
    public void deleteTicketType(Long id) {
        TicketType t = ticketTypeRepository.findById(id)
                .filter(x -> !x.getDeletedTicketType())
                .orElseThrow(() -> new ResourceNotFoundException("Tipo de ticket", "id", id));
        t.setDeletedTicketType(true);
        ticketTypeRepository.save(t);
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

    public BrandingConfig updateBranding(BrandingConfig config) {
        BrandingConfig current = getBranding();
        if (config.getPrimaryColor() != null) current.setPrimaryColor(config.getPrimaryColor());
        if (config.getSecondaryColor() != null) current.setSecondaryColor(config.getSecondaryColor());
        if (config.getBackgroundUrl() != null && !config.getBackgroundUrl().isBlank()) {
            current.setBackgroundUrl(config.getBackgroundUrl());
        }
        return brandingConfigRepository.save(current);
    }

    private Map<String, String> saveImage(MultipartFile file, String prefix) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("El archivo está vacío");
        }
        try {
            Path dir = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            String original = file.getOriginalFilename();
            String ext = "";
            if (original != null && original.contains(".")) {
                ext = original.substring(original.lastIndexOf('.'));
            }
            if (ext.isBlank()) ext = ".png";
            String fileName = prefix + "_" + System.currentTimeMillis() + ext.toLowerCase();
            Path target = dir.resolve(fileName);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            Map<String, String> res = new HashMap<>();
            res.put("url", "/uploads/" + fileName);
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

    // ============================ ÁREAS ============================

    @Transactional(readOnly = true)
    public List<Area> getAreas() {
        return areaRepository.findByDeletedAreaFalseOrderByPriorityLevelDesc();
    }

    public Area saveArea(Long id, String name, String description, Integer priorityLevel, Boolean status) {
        Area area = id != null
                ? areaRepository.findById(id).filter(a -> !a.getDeletedArea())
                        .orElseThrow(() -> new ResourceNotFoundException("Área", "id", id))
                : new Area();
        if (name != null) area.setNameArea(name);
        if (description != null) area.setDescriptionArea(description);
        if (priorityLevel != null) area.setPriorityLevel(priorityLevel);
        if (status != null) area.setStatusArea(status);
        if (area.getCreatedAtArea() == null) {
            // nuevo
            if (areaRepository.existsByNameArea(name)) {
                throw new BadRequestException("El área ya existe: " + name);
            }
        }
        return areaRepository.save(area);
    }

    public void deleteArea(Long id) {
        Area area = areaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Área", "id", id));
        area.setDeletedArea(true);
        areaRepository.save(area);
    }

    // ============================ CATEGORÍAS ============================

    @Transactional(readOnly = true)
    public List<Category> getCategories() {
        return categoryRepository.findByDeletedCategoryFalse();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getCategoriesForAdmin() {
        return categoryRepository.findByDeletedCategoryFalse().stream().map(c -> {
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
            return m;
        }).toList();
    }

    public Category saveCategory(Long id, String name, String description, Long teamId,
                                 String icon, String color) {
        Category category = id != null
                ? categoryRepository.findById(id).filter(c -> !c.getDeletedCategory())
                        .orElseThrow(() -> new ResourceNotFoundException("Categoría", "id", id))
                : new Category();
        if (name != null) category.setNameCategory(name);
        if (description != null) category.setDescriptionCategory(description);
        if (icon != null) category.setIconCategory(icon);
        if (color != null) category.setColorCategory(color);
        if (teamId != null) {
            category.setTeamCategory(teamRepository.findById(teamId)
                    .orElseThrow(() -> new ResourceNotFoundException("Equipo", "id", teamId)));
        }
        if (category.getIdCategory() == null && categoryRepository.existsByNameCategory(name)) {
            throw new BadRequestException("La categoría ya existe: " + name);
        }
        return categoryRepository.save(category);
    }

    public void deleteCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Categoría", "id", id));
        category.setDeletedCategory(true);
        categoryRepository.save(category);
    }

    // ============================ EQUIPOS ============================

    @Transactional(readOnly = true)
    public List<SupportTeam> getTeams() {
        return teamRepository.findByActiveTeamTrue();
    }

    public SupportTeam saveTeam(Long id, String name, String description, String method, Boolean active) {
        SupportTeam team = id != null
                ? teamRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Equipo", "id", id))
                : new SupportTeam();
        if (name != null) team.setNameTeam(name);
        if (description != null) team.setDescriptionTeam(description);
        if (method != null) team.setAssignmentMethod(method);
        if (active != null) team.setActiveTeam(active);
        if (team.getIdTeam() == null && teamRepository.existsByNameTeam(name)) {
            throw new BadRequestException("El equipo ya existe: " + name);
        }
        return teamRepository.save(team);
    }

    public void addMemberToTeam(Long teamId, Long userId) {
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

    public void removeMemberFromTeam(Long teamId, Long userId) {
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

    public SlaPolicy saveSlaPolicy(Long id, String name, String description,
                                   Integer responseMinutes, Integer resolutionMinutes, Boolean active) {
        SlaPolicy policy = id != null
                ? slaPolicyRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("SLA", "id", id))
                : new SlaPolicy();
        if (name != null) policy.setNameSlaPolicy(name);
        if (description != null) policy.setDescriptionSlaPolicy(description);
        if (responseMinutes != null) policy.setResponseTimeMinutes(responseMinutes);
        if (resolutionMinutes != null) policy.setResolutionTimeMinutes(resolutionMinutes);
        if (active != null) policy.setActiveSlaPolicy(active);
        if (policy.getIdSlaPolicy() == null && slaPolicyRepository.existsByNameSlaPolicy(name)) {
            throw new BadRequestException("La política SLA ya existe: " + name);
        }
        return slaPolicyRepository.save(policy);
    }

    public SlaRule saveSlaRule(Long id, Long policyId, Long areaId, Long priorityId,
                               Long categoryId, Long typeId, Boolean active) {
        SlaRule rule = id != null
                ? slaRuleRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Regla SLA", "id", id))
                : new SlaRule();
        rule.setSlaPolicy(slaPolicyRepository.findById(policyId)
                .orElseThrow(() -> new ResourceNotFoundException("Política SLA", "id", policyId)));
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
            m.put("areaId", r.getArea() != null ? r.getArea().getIdArea() : null);
            m.put("priorityId", r.getPriority() != null ? r.getPriority().getIdPriority() : null);
            m.put("categoryId", r.getCategory() != null ? r.getCategory().getIdCategory() : null);
            m.put("typeId", r.getType() != null ? r.getType().getIdTicketType() : null);
            m.put("activeSlaRule", r.getActiveSlaRule());
            return m;
        }).toList();
    }

    public void deleteSlaPolicy(Long id) {
        SlaPolicy p = slaPolicyRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("SLA", "id", id));
        p.setActiveSlaPolicy(false);
        slaPolicyRepository.save(p);
    }

    public void deleteSlaRule(Long id) {
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

    public SlaEscalation saveEscalation(Long id, Long policyId, Integer trigger, String action,
                                        Long teamId, Long userId, Long templateId) {
        SlaEscalation escalation = id != null
                ? escalationRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Escalamiento", "id", id))
                : new SlaEscalation();
        escalation.setSlaPolicy(slaPolicyRepository.findById(policyId)
                .orElseThrow(() -> new ResourceNotFoundException("Política SLA", "id", policyId)));
        if (trigger != null) escalation.setTriggerPercentage(trigger);
        if (action != null) escalation.setActionType(action);
        if (teamId != null) escalation.setDestinationTeam(teamRepository.findById(teamId).orElse(null));
        if (userId != null) escalation.setDestinationUser(userRepository.findById(userId).orElse(null));
        escalation.setNotificationTemplate(templateRepository.findById(templateId).orElse(null));
        return escalationRepository.save(escalation);
    }

    public void deleteEscalation(Long id) {
        escalationRepository.deleteById(id);
    }

    // ============================ PLANTILLAS ============================

    @Transactional(readOnly = true)
    public List<NotificationTemplate> getTemplates() {
        return templateRepository.findAll();
    }

    public NotificationTemplate saveTemplate(Long id, String name, String type, String subject, String body, Boolean active) {
        NotificationTemplate template = id != null
                ? templateRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Plantilla", "id", id))
                : new NotificationTemplate();
        if (name != null) template.setNameTemplate(name);
        if (type != null) template.setTypeTemplate(type);
        if (subject != null) template.setSubjectTemplate(subject);
        if (body != null) template.setBodyTemplate(body);
        if (active != null) template.setActiveTemplate(active);
        if (template.getIdTemplate() == null && templateRepository.existsByNameTemplate(name)) {
            throw new BadRequestException("La plantilla ya existe: " + name);
        }
        return templateRepository.save(template);
    }

    public void deleteTemplate(Long id) {
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
    public List<TeamUser> teamMembers(Long teamId) {
        return teamUserRepository.findByTeamIdTeamAndActiveTeamUserTrue(teamId);
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

    public AiConfigResponse saveAiConfig(AiConfigRequest request, Long updatedBy) {
        AiConfig cfg = aiConfigRepository.findFirstByOrderByIdAiConfigAsc().orElseGet(AiConfig::new);
        cfg.setProvider(request.provider().toUpperCase());
        cfg.setModel(request.model());
        cfg.setBaseUrl(request.baseUrl());
        cfg.setEnabled(Boolean.TRUE.equals(request.enabled()));
        if (request.apiKey() != null && !request.apiKey().isBlank()) {
            cfg.setApiKey(request.apiKey().trim());
        }
        cfg.setUpdatedBy(updatedBy);
        AiConfig saved = aiConfigRepository.save(cfg);
        return toAiResponse(saved);
    }

    private AiConfigResponse toAiResponse(AiConfig cfg) {
        return new AiConfigResponse(
                cfg.getIdAiConfig(),
                cfg.getEnabled(),
                cfg.getProvider(),
                cfg.getModel(),
                maskKey(cfg.getApiKey()),
                cfg.getBaseUrl());
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
}