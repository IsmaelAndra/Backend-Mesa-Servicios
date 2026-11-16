package com.mesaayuda.reportes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mesaayuda.entity.ReporteUsuario;
import com.mesaayuda.entity.User;
import com.mesaayuda.exception.BadRequestException;
import com.mesaayuda.exception.ResourceNotFoundException;
import com.mesaayuda.repository.ReporteUsuarioRepository;
import com.mesaayuda.security.Roles;
import com.mesaayuda.service.CustomReportService;
import com.mesaayuda.service.JefaturaService;
import com.mesaayuda.service.SystemAuditService;
import com.mesaayuda.service.UserService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Reportes hechos desde el modal. Los crean el súper admin y los jefes; el
 * auditor los ve y los exporta. Al ejecutarse, el jefe solo obtiene filas de
 * las áreas a su cargo, sea quien sea el autor del reporte.
 */
@Service
public class ReporteUsuarioService {

    /** Filas de la vista previa. La exportación usa el límite general de reportes. */
    public static final int FILAS_VISTA_PREVIA = 200;

    private final ReporteUsuarioRepository repo;
    private final UserService userService;
    private final JefaturaService jefaturaService;
    private final CustomReportService motor;
    private final SystemAuditService auditoria;
    private final ObjectMapper json;

    public ReporteUsuarioService(ReporteUsuarioRepository repo, UserService userService,
                                 JefaturaService jefaturaService, CustomReportService motor,
                                 SystemAuditService auditoria, ObjectMapper json) {
        this.repo = repo;
        this.userService = userService;
        this.jefaturaService = jefaturaService;
        this.motor = motor;
        this.auditoria = auditoria;
        this.json = json;
    }

    /** sistema: reporte del sistema (sin dueño; solo lo edita el súper admin). */
    public record ReporteResponse(UUID id, String nombre, String descripcion, String icono, String color,
                                  String propietario, boolean propio, boolean sistema,
                                  ConstructorConsulta.Definicion definicion, OffsetDateTime actualizado) {
    }

    public record VistaPrevia(List<String> columnas, List<List<String>> filas, boolean recortada) {
    }

    // ------------------------------------------------------------------ catálogo

    /** Tablas, columnas y filtros que ofrece el modal. */
    public List<Map<String, Object>> catalogo() {
        return CatalogoReportes.TABLAS.values().stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("clave", t.clave());
            m.put("nombre", t.nombre());
            m.put("descripcion", t.descripcion());
            m.put("icono", t.icono());
            m.put("columnas", t.columnas().values().stream()
                    .map(c -> Map.of("clave", c.clave(), "etiqueta", c.etiqueta(), "agrupable", c.agrupable())).toList());
            m.put("medidas", t.medidas().values().stream()
                    .map(x -> Map.of("clave", x.clave(), "etiqueta", x.etiqueta())).toList());
            m.put("filtros", t.filtros().values().stream()
                    .map(f -> Map.of("clave", f.clave(), "etiqueta", f.etiqueta(), "tipo", f.tipo().name())).toList());
            return m;
        }).toList();
    }

    // ------------------------------------------------------------------ CRUD

    @Transactional(readOnly = true)
    public List<ReporteResponse> listar() {
        User actual = actual();
        List<ReporteUsuario> lista = Roles.veTodo(rol(actual))
                ? repo.findByDeletedFalseOrderBySistemaDescUpdatedAtDesc()
                : repo.propiosYDelSistema(actual.getIdUser());
        return lista.stream().map(r -> dto(r, actual)).toList();
    }

    @Transactional
    public ReporteResponse guardar(UUID id, String nombre, String descripcion, ConstructorConsulta.Definicion def) {
        return guardar(id, nombre, descripcion, null, null, def);
    }

    @Transactional
    public ReporteResponse guardar(UUID id, String nombre, String descripcion, String icono, String color,
                                   ConstructorConsulta.Definicion def) {
        User actual = actual();
        exigirCreador(actual);
        if (nombre == null || nombre.isBlank()) {
            throw new BadRequestException("Ponga un nombre al reporte");
        }
        ConstructorConsulta.validar(def);
        ReporteUsuario r;
        if (id == null) {
            r = new ReporteUsuario();
            r.setPropietario(actual);
        } else {
            r = existente(id);
            exigirDuenio(r, actual, "editar");
        }
        r.setNombre(nombre.trim().length() > 150 ? nombre.trim().substring(0, 150) : nombre.trim());
        r.setDescripcion(descripcion == null || descripcion.isBlank() ? null
                : (descripcion.trim().length() > 500 ? descripcion.trim().substring(0, 500) : descripcion.trim()));
        r.setDefinicion(aJson(def));
        // Solo una clase de Font Awesome y un color #rrggbb: se pintan en la tarjeta.
        r.setIcono(icono != null && icono.matches("^[a-z0-9 -]{3,100}$") ? icono.trim() : null);
        r.setColor(color != null && color.matches("^#[0-9A-Fa-f]{6}$") ? color : null);
        r = repo.save(r);
        auditoria.record(id == null ? "REPORTE_CREADO" : "REPORTE_EDITADO", "ReporteUsuario",
                r.getIdReporteUsuario(), actual.getCorporateEmailUser(), r.getNombre());
        return dto(r, actual);
    }

    @Transactional
    public void eliminar(UUID id) {
        User actual = actual();
        ReporteUsuario r = existente(id);
        // Los reportes del sistema vienen de fábrica: se editan, pero no se borran.
        if (Boolean.TRUE.equals(r.getSistema())) {
            throw new com.mesaayuda.exception.BadRequestException("Los reportes del sistema no se eliminan; puede editarlos");
        }
        exigirDuenio(r, actual, "eliminar");
        r.setDeleted(true);
        repo.save(r);
        auditoria.record("REPORTE_ELIMINADO", "ReporteUsuario", id, actual.getCorporateEmailUser(), r.getNombre());
    }

    // ------------------------------------------------------------------ ejecución

    /** Vista previa de una definición (guardada o no), acotada al ámbito de quien la pide. */
    @Transactional(readOnly = true)
    public VistaPrevia vistaPrevia(ConstructorConsulta.Definicion def, LocalDate desde, LocalDate hasta) {
        User actual = actual();
        exigirLector(actual);
        ConstructorConsulta.Consulta c = ConstructorConsulta.construir(def, desde, hasta, ambito(actual),
                FILAS_VISTA_PREVIA + 1);
        List<Map<String, Object>> filas = motor.ejecutarConsulta(c.sql(), c.parametros());
        boolean recortada = filas.size() > FILAS_VISTA_PREVIA;
        List<Map<String, Object>> visibles = recortada ? filas.subList(0, FILAS_VISTA_PREVIA) : filas;
        return new VistaPrevia(c.encabezados(), CustomReportService.comoTexto(visibles, c.encabezados()), recortada);
    }

    @Transactional(readOnly = true)
    public VistaPrevia vistaPrevia(UUID id, LocalDate desde, LocalDate hasta) {
        return vistaPrevia(definicion(legible(id)), desde, hasta);
    }

    @Transactional(readOnly = true)
    public byte[] exportar(UUID id, String formato, LocalDate desde, LocalDate hasta, String orientacion) {
        User actual = actual();
        ReporteUsuario r = legible(id);
        ConstructorConsulta.Consulta c = ConstructorConsulta.construir(definicion(r), desde, hasta, ambito(actual), null);
        List<Map<String, Object>> filas = motor.ejecutarConsulta(c.sql(), c.parametros());
        auditoria.record("REPORTE_EXPORTADO", "ReporteUsuario", id, actual.getCorporateEmailUser(),
                r.getNombre() + " (" + formato + ", " + filas.size() + " filas)");
        return motor.renderizar(r.getNombre(), filas, formato, actual.getIdUser(), orientacion);
    }

    public String nombre(UUID id) {
        return legible(id).getNombre();
    }

    // ------------------------------------------------------------------ permisos

    /** null = todas las áreas (súper admin, auditor); el jefe, las suyas. */
    private Set<UUID> ambito(User u) {
        return Roles.veTodo(rol(u)) ? null : jefaturaService.areasACargo(u);
    }

    private ReporteUsuario legible(UUID id) {
        User actual = actual();
        exigirLector(actual);
        ReporteUsuario r = existente(id);
        if (!Roles.veTodo(rol(actual)) && !Boolean.TRUE.equals(r.getSistema()) && !esDuenio(r, actual)) {
            throw new AccessDeniedException("Este reporte es de otra persona");
        }
        return r;
    }

    private static void exigirLector(User u) {
        String rol = rol(u);
        if (!Roles.veTodo(rol) && !Roles.esJefe(rol)) {
            throw new AccessDeniedException("Los reportes son del súper administrador, los jefes y el auditor");
        }
    }

    private static void exigirCreador(User u) {
        String rol = rol(u);
        if (!Roles.esSuperAdmin(rol) && !Roles.esJefe(rol)) {
            throw new AccessDeniedException("Crean reportes el súper administrador y los jefes de área");
        }
    }

    private static void exigirDuenio(ReporteUsuario r, User u, String que) {
        // Un reporte del sistema no tiene dueño: solo lo toca el súper admin.
        if (!Roles.esSuperAdmin(rol(u)) && !esDuenio(r, u)) {
            throw new AccessDeniedException("Solo quien lo creó o el súper administrador puede " + que + " este reporte");
        }
    }

    private static boolean esDuenio(ReporteUsuario r, User u) {
        return r.getPropietario() != null && u != null && r.getPropietario().getIdUser().equals(u.getIdUser());
    }

    private ReporteUsuario existente(UUID id) {
        return repo.findById(id).filter(r -> !Boolean.TRUE.equals(r.getDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Reporte", "id", id));
    }

    private User actual() {
        return userService.getCurrentUser();
    }

    private static String rol(User u) {
        return u == null || u.getRolUser() == null ? null : u.getRolUser().getNameRole();
    }

    private ReporteResponse dto(ReporteUsuario r, User actual) {
        return new ReporteResponse(r.getIdReporteUsuario(), r.getNombre(), r.getDescripcion(),
                r.getIcono(), r.getColor(),
                r.getPropietario() == null ? "Sistema" : r.getPropietario().getCorporateEmailUser(),
                esDuenio(r, actual), Boolean.TRUE.equals(r.getSistema()), definicion(r), r.getUpdatedAt());
    }

    private String aJson(ConstructorConsulta.Definicion d) {
        try {
            return json.writeValueAsString(d);
        } catch (JsonProcessingException e) {
            throw new BadRequestException("Definición de reporte no válida");
        }
    }

    private ConstructorConsulta.Definicion definicion(ReporteUsuario r) {
        try {
            return json.readValue(r.getDefinicion(), ConstructorConsulta.Definicion.class);
        } catch (JsonProcessingException e) {
            throw new BadRequestException("El reporte guardado está dañado; vuelva a crearlo");
        }
    }
}
