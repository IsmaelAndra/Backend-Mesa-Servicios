package com.mesaayuda.service;

import com.mesaayuda.dto.GlobalSearchResponse;
import com.mesaayuda.dto.SearchAreaItem;
import com.mesaayuda.dto.SearchAssetItem;
import com.mesaayuda.dto.SearchTeamItem;
import com.mesaayuda.dto.SearchTicketItem;
import com.mesaayuda.dto.SearchUserItem;
import com.mesaayuda.dto.SearchWorkflowItem;
import com.mesaayuda.dto.TicketResponse;
import com.mesaayuda.entity.Area;
import com.mesaayuda.entity.Asset;
import com.mesaayuda.entity.SupportTeam;
import com.mesaayuda.entity.User;
import com.mesaayuda.entity.Workflow;
import com.mesaayuda.repository.AreaRepository;
import com.mesaayuda.repository.AssetRepository;
import com.mesaayuda.repository.SupportTeamRepository;
import com.mesaayuda.repository.WorkflowRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.List;

/**
 * Búsqueda global ({@code GET /search}).
 *
 * <p>Resuelve un término contra varios catálogos a la vez y devuelve un máximo
 * de {@link #LIMITE_POR_GRUPO} elementos por grupo. Cada grupo se comporta con
 * los permisos del rol que consulta y reutiliza los caminos de autorización ya
 * existentes en lugar de reimplementarlos:
 * <ul>
 *   <li><b>Tickets</b>: pasa por {@link TicketService#search}, que ya aplica
 *       el corte "un solicitante solo ve los suyos" ({@code TicketAccessPolicy}).
 *   <li><b>Usuarios y agentes</b>: solo personal (AGENT, ADMIN, SUPER_ADMIN),
 *       y devuelve el mínimo espejo de persona (nunca identificación, teléfono,
 *       UPN ni flags de Microsoft), el mismo criterio que {@code AgentOptionResponse}.
 *   <li><b>Equipos y áreas</b>: dato de referencia, visible para cualquier rol
 *       autenticado.
 *   <li><b>Workflows</b>: solo SUPER_ADMIN, el mismo rol que gobierna
 *       {@code /admin/workflows} y el constructor de automatizaciones.
 *   <li><b>Activos</b>: todos los roles, con el mismo alcance que el listado
 *       ({@link AssetService#alcanceVisible}): cada uno los suyos, el jefe de área
 *       los de su área y ADMIN/SUPER_ADMIN todos.
 * </ul>
 *
 * <p>Los términos de los filtros LIKE se escapan antes de construir la consulta
 * para que {@code %} y {@code _} del usuario no actúen como comodines. La
 * búsqueda indexa con {@code pg_trgm} en los campos de texto de cada entidad
 * (ver {@code schema.sql}).
 */
@Service
public class SearchService {

    /** Tope de resultados por grupo, para devolver siempre una página pequeña. */
    public static final int LIMITE_POR_GRUPO = 5;

    @Autowired
    private TicketService ticketService;

    @Autowired
    private UserService userService;

    @Autowired
    private TicketAccessPolicy accessPolicy;

    @Autowired
    private AreaRepository areaRepository;

    @Autowired
    private SupportTeamRepository supportTeamRepository;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private AssetService assetService;

    @Autowired
    private WorkflowRepository workflowRepository;

    @Transactional(readOnly = true)
    public GlobalSearchResponse buscar(String query) {
        User actual = userService.getCurrentUser();
        // En minúsculas: las columnas se comparan con lower(...). Antes el término
        // iba tal cual y "Ismael", "TI" o "Quito" no encontraban nada (los tickets
        // sí, porque TicketService ya lo pasaba a minúsculas).
        String termino = escapar(query.trim().toLowerCase(java.util.Locale.ROOT));

        Pageable limite = PageRequest.of(0, LIMITE_POR_GRUPO);
        boolean personal = accessPolicy.esPersonal(actual);
        boolean superAdmin = actual != null && actual.getRolUser() != null
                && "SUPER_ADMIN".equals(actual.getRolUser().getNameRole());

        List<SearchTicketItem> tickets = buscarTickets(actual, termino, limite);
        List<SearchUserItem> users = List.of();
        List<SearchUserItem> agents = List.of();
        List<SearchWorkflowItem> workflows = List.of();
        List<SearchAssetItem> assets = List.of();

        if (personal) {
            // Solicitantes: usuarios y jefes de área. Personal: agentes y administradores.
            users = buscarPersonas(termino, List.of("USER", "AREA_MANAGER"), limite);
            agents = buscarPersonas(termino, List.of("AGENT", "ADMIN", "SUPER_ADMIN"), limite);
        }
        if (superAdmin) {
            workflows = buscarWorkflows(termino, limite);
        }
        if (actual != null) {
            assets = buscarActivos(actual, termino, limite);
        }

        List<SearchTeamItem> teams = buscarEquipos(termino, limite);
        List<SearchAreaItem> areas = buscarAreas(termino, limite);

        return new GlobalSearchResponse(query.trim(), tickets, users, agents, teams, areas, workflows, assets);
    }

    /**
     * Tickets. Reutiliza {@code TicketService.search} para no duplicar el corte
     * por rol: un solicitante recibe solo los suyos y el personal recibe todos
     * los que coinciden. Mapea la respuesta completa a la vista compacta.
     */
    private List<SearchTicketItem> buscarTickets(User actual, String termino, Pageable limite) {
        // ADMIN y SUPER_ADMIN buscan en todos. El resto, solo en los suyos (un
        // agente, los que abrió y los que tiene asignados): lo impone
        // TicketService.search, igual que en la lista de tickets.
        return aItems(ticketService.search(null, null, null, null, null, null,
                termino, null, limite.getPageNumber(), limite.getPageSize()).getContent());
    }

    private static List<SearchTicketItem> aItems(List<TicketResponse> lista) {
        return lista.stream()
                .map(t -> new SearchTicketItem(t.idTicket(), t.titleTicket(), t.status(),
                        t.priority(), t.createdAtTicket(), t.numberTicket()))
                .toList();
    }

    /**
     * Personas de un rol. El término busca en correo y nombre, no en
     * identificación: la identificación no se debe usar como llave de búsqueda
     * pública porque es dato sensible y porque la búsqueda global no la expone.
     */
    private List<SearchUserItem> buscarPersonas(String termino, List<String> roles, Pageable limite) {
        Specification<User> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("deletedUser")));
            // Solo cuentas activas: elegir a alguien abre un ticket a su nombre.
            predicates.add(cb.equal(root.get("statusUser"), "ACTIVE"));
            predicates.add(root.get("rolUser").get("nameRole").in(roles));
            predicates.add(cb.or(
                    cb.like(cb.lower(root.get("corporateEmailUser")), "%" + termino + "%"),
                    cb.like(cb.lower(root.get("displayName")), "%" + termino + "%"),
                    cb.like(cb.lower(root.get("firstName")), "%" + termino + "%"),
                    cb.like(cb.lower(root.get("lastName")), "%" + termino + "%")
            ));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return userService.findAllPorSpec(spec, limite).stream()
                .map(u -> new SearchUserItem(u.getIdUser(), u.getFullName(),
                        u.getCorporateEmailUser(), u.getRolUser().getNameRole(),
                        areaDe(u), u.getPhotoUrl()))
                .toList();
    }

    /** Solo equipos activos: un equipo desactivado no debe aparecer como destino. */
    private List<SearchTeamItem> buscarEquipos(String termino, Pageable limite) {
        Specification<SupportTeam> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isTrue(root.get("activeTeam")));
            predicates.add(cb.like(cb.lower(root.get("nameTeam")), "%" + termino + "%"));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return supportTeamRepository.findAll(spec, limite).stream()
                .map(t -> new SearchTeamItem(t.getIdTeam(), t.getNameTeam(), t.getDescriptionTeam()))
                .toList();
    }

    /** Solo áreas no borradas y activas, misma idea que los equipos. */
    private List<SearchAreaItem> buscarAreas(String termino, Pageable limite) {
        Specification<Area> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("deletedArea")));
            predicates.add(cb.isTrue(root.get("statusArea")));
            predicates.add(cb.like(cb.lower(root.get("nameArea")), "%" + termino + "%"));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return areaRepository.findAll(spec, limite).stream()
                .map(a -> new SearchAreaItem(a.getIdArea(), a.getNameArea(), a.getDescriptionArea()))
                .toList();
    }

    /** Solo SUPER_ADMIN, vía {@link SearchService} o {@code /admin/workflows}. */
    private List<SearchWorkflowItem> buscarWorkflows(String termino, Pageable limite) {
        return workflowRepository.buscarPorTexto(termino, limite).stream()
                .map(w -> new SearchWorkflowItem(w.getIdWorkflow(), w.getNameWorkflow(),
                        w.getStatusWorkflow()))
                .toList();
    }

    /**
     * Activos de TI, con el mismo alcance que el listado de activos: antes no
     * filtraba nada y cualquier agente veía aquí los activos de todas las áreas.
     */
    private List<SearchAssetItem> buscarActivos(User actual, String termino, Pageable limite) {
        Specification<Asset> coincide = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.or(
                    cb.like(cb.lower(root.get("codeAsset")), "%" + termino + "%"),
                    cb.like(cb.lower(root.get("nameAsset")), "%" + termino + "%"),
                    cb.like(cb.lower(root.get("brandAsset")), "%" + termino + "%"),
                    cb.like(cb.lower(root.get("modelAsset")), "%" + termino + "%"),
                    cb.like(cb.lower(root.get("serialNumberAsset")), "%" + termino + "%"),
                    cb.like(cb.lower(root.get("typeAsset")), "%" + termino + "%")
            ));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return assetRepository.findAll(assetService.alcanceVisible(actual).and(coincide), limite).stream()
                .map(a -> new SearchAssetItem(
                        a.getIdAsset(),
                        a.getCodeAsset(),
                        a.getNameAsset(),
                        a.getTypeAsset(),
                        a.getStatusAsset(),
                        a.getAssignedUser() != null ? a.getAssignedUser().getCorporateEmailUser() : null,
                        a.getAssignedUser() != null ? a.getAssignedUser().getFullName() : null,
                        a.getArea() != null ? a.getArea().getNameArea() : null
                ))
                .toList();
    }

    private String areaDe(User u) {
        if (u.getArea() == null) {
            return null;
        }
        return u.getArea().getNameArea();
    }

    /**
     * Escapa los comodines de LIKE ({@code %}, {@code _} y la propia barra), de
     * modo que el término del usuario se trate como texto literal. JPQL y el
     * LIKE de PostgreSQL usan {@code \} como carácter de escape por defecto.
     */
    private String escapar(String termino) {
        return termino.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}