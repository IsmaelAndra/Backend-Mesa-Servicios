package com.mesaayuda.plantillas;

import com.mesaayuda.entity.NotificationTemplate;
import com.mesaayuda.entity.User;
import com.mesaayuda.exception.BadRequestException;
import com.mesaayuda.repository.AreaRepository;
import com.mesaayuda.repository.NotificationTemplateRepository;
import com.mesaayuda.security.Roles;
import com.mesaayuda.service.JefaturaService;
import com.mesaayuda.service.SystemAuditService;
import com.mesaayuda.service.UserService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Plantillas de correo por área (V38). Solo los avisos de tickets se pueden
 * personalizar (son los que el área "firma" ante el solicitante); el resto
 * (cuentas, actividades, activos...) son del sistema y se editan en Plantillas.
 * El súper admin edita las de cualquier área; el jefe, las de la suya; el
 * auditor solo las ve.
 */
@Service
public class PlantillaAreaService {

    /** Plantillas que un área puede personalizar, en el orden de la pantalla. */
    public static final List<String> PERSONALIZABLES = List.of(
            "TICKET_CREADO", "TICKET_ASIGNADO", "TICKET_ACTUALIZADO", "TICKET_RESUELTO", "TICKET_CERRADO",
            "CHAT_MENSAJE", "RATING_REQUEST", "SLA_WARNING", "SLA_BREACHED", "ESCALATION", "ACCESO_CONFIDENCIAL");

    private static final Pattern SCRIPT = Pattern.compile("(?is)<\\s*script[^>]*>.*?<\\s*/\\s*script\\s*>");
    private static final Pattern MANEJADOR = Pattern.compile("(?i)\\son[a-z]+\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)");
    private static final Pattern JS_URL = Pattern.compile("(?i)javascript\\s*:");

    private final PlantillaAreaRepository repo;
    private final NotificationTemplateRepository plantillas;
    private final AreaRepository areas;
    private final UserService userService;
    private final JefaturaService jefaturaService;
    private final SystemAuditService auditoria;

    public PlantillaAreaService(PlantillaAreaRepository repo, NotificationTemplateRepository plantillas,
                                AreaRepository areas, UserService userService, JefaturaService jefaturaService,
                                SystemAuditService auditoria) {
        this.repo = repo;
        this.plantillas = plantillas;
        this.areas = areas;
        this.userService = userService;
        this.jefaturaService = jefaturaService;
        this.auditoria = auditoria;
    }

    /** Una plantilla tal como la ve el área: la general y, si existe, la suya. */
    public record Vista(String nombre, String tipo, String icono, String color,
                        String asuntoGeneral, String cuerpoGeneral,
                        boolean personalizada, String asunto, String cuerpo, boolean activa,
                        OffsetDateTime actualizada) {}

    public record Guardar(String asunto, String cuerpo, Boolean activa) {}

    /** Áreas que puede ver quien llama (null = todas: súper admin o auditor). */
    public Set<UUID> areasVisibles() {
        User u = userService.getCurrentUser();
        String rol = rol(u);
        if (Roles.SUPER_ADMIN.equals(rol) || Roles.AUDITOR.equals(rol)) {
            return null;
        }
        if (Roles.JEFE.equals(rol)) {
            return jefaturaService.areasACargo(u);
        }
        throw new AccessDeniedException("Solo el súper admin y los jefes de área personalizan plantillas");
    }

    @Transactional(readOnly = true)
    public List<Vista> listar(UUID areaId) {
        exigirLectura(areaId);
        Map<String, PlantillaArea> propias = new HashMap<>();
        repo.findByAreaId(areaId).forEach(p -> propias.put(p.getNombre(), p));
        List<Vista> out = new ArrayList<>();
        for (String nombre : PERSONALIZABLES) {
            NotificationTemplate g = plantillas.findByNameTemplate(nombre).orElse(null);
            PlantillaArea p = propias.get(nombre);
            out.add(new Vista(nombre,
                    g == null ? "TICKET" : g.getTypeTemplate(),
                    g == null ? null : g.getIconTemplate(),
                    g == null ? null : g.getColorTemplate(),
                    g == null ? "" : g.getSubjectTemplate(),
                    g == null ? "" : g.getBodyTemplate(),
                    p != null,
                    p != null ? p.getAsunto() : (g == null ? "" : g.getSubjectTemplate()),
                    p != null ? p.getCuerpo() : (g == null ? "" : g.getBodyTemplate()),
                    p == null || Boolean.TRUE.equals(p.getActiva()),
                    p == null ? null : p.getUpdatedAt()));
        }
        return out;
    }

    @Transactional
    public Vista guardar(UUID areaId, String nombre, Guardar r) {
        exigirEscritura(areaId);
        exigirPersonalizable(nombre);
        if (r == null || r.asunto() == null || r.asunto().isBlank() || r.cuerpo() == null || r.cuerpo().isBlank()) {
            throw new BadRequestException("El asunto y el cuerpo son obligatorios");
        }
        if (r.asunto().length() > 255) {
            throw new BadRequestException("El asunto no puede pasar de 255 caracteres");
        }
        if (r.cuerpo().length() > 200_000) {
            throw new BadRequestException("El cuerpo es demasiado largo");
        }
        PlantillaArea p = repo.findByNombreAndAreaId(nombre, areaId).orElseGet(PlantillaArea::new);
        p.setNombre(nombre);
        p.setAreaId(areaId);
        p.setAsunto(r.asunto().trim());
        p.setCuerpo(limpiar(r.cuerpo()));
        p.setActiva(r.activa() == null || r.activa());
        p.setEditadaPor(userService.getCurrentUser().getIdUser());
        repo.save(p);
        auditoria.record("PLANTILLA_AREA", "PlantillaArea", p.getIdPlantillaArea(),
                userService.getCurrentUser().getCorporateEmailUser(), nombre + " · " + nombreArea(areaId));
        return listar(areaId).stream().filter(v -> v.nombre().equals(nombre)).findFirst().orElseThrow();
    }

    /** Vuelve a la plantilla general. */
    @Transactional
    public void restablecer(UUID areaId, String nombre) {
        exigirEscritura(areaId);
        exigirPersonalizable(nombre);
        repo.findByNombreAndAreaId(nombre, areaId).ifPresent(p -> {
            repo.delete(p);
            auditoria.record("PLANTILLA_AREA_RESTABLECIDA", "PlantillaArea", p.getIdPlantillaArea(),
                    userService.getCurrentUser().getCorporateEmailUser(), nombre + " · " + nombreArea(areaId));
        });
    }

    /**
     * Quita lo que un cliente de correo podría ejecutar o usar para engañar
     * (scripts, manejadores on*=, URLs javascript:). La escribe un jefe, no solo
     * el súper admin, así que no se guarda tal cual.
     */
    public static String limpiar(String html) {
        String s = SCRIPT.matcher(html).replaceAll("");
        s = MANEJADOR.matcher(s).replaceAll("");
        return JS_URL.matcher(s).replaceAll("");
    }

    private void exigirPersonalizable(String nombre) {
        if (!PERSONALIZABLES.contains(nombre)) {
            throw new BadRequestException("Esa plantilla no se personaliza por área");
        }
    }

    private void exigirLectura(UUID areaId) {
        if (areaId == null || !areas.existsById(areaId)) {
            throw new BadRequestException("Elija un área");
        }
        Set<UUID> visibles = areasVisibles();
        if (visibles != null && !visibles.contains(areaId)) {
            throw new AccessDeniedException("Solo puede ver las plantillas de su área");
        }
    }

    private void exigirEscritura(UUID areaId) {
        exigirLectura(areaId);
        if (Roles.AUDITOR.equals(rol(userService.getCurrentUser()))) {
            throw new AccessDeniedException("El auditor solo puede ver las plantillas");
        }
    }

    private String nombreArea(UUID id) {
        return areas.findById(id).map(a -> a.getNameArea()).orElse(String.valueOf(id));
    }

    private static String rol(User u) {
        return u.getRolUser() == null ? null : u.getRolUser().getNameRole();
    }
}
