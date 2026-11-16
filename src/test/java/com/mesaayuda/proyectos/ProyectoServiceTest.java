package com.mesaayuda.proyectos;

import com.mesaayuda.entity.Area;
import com.mesaayuda.entity.Role;
import com.mesaayuda.entity.User;
import com.mesaayuda.repository.AreaRepository;
import com.mesaayuda.repository.UserRepository;
import com.mesaayuda.service.JefaturaService;
import com.mesaayuda.service.NotificationService;
import com.mesaayuda.service.SystemAuditService;
import com.mesaayuda.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.mesaayuda.IdsPrueba.id;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Reglas de los proyectos: área dueña, invitación aceptada por el otro jefe y avance ponderado. */
class ProyectoServiceTest {

    private static final UUID TECNOLOGIA = id(1);
    private static final UUID CONTABILIDAD = id(2);
    private static final UUID PROYECTO = id(50);

    private final ProyectoRepository proyectos = mock(ProyectoRepository.class);
    private final ProyectoAreaRepository areasProyecto = mock(ProyectoAreaRepository.class);
    private final ProyectoMiembroRepository miembros = mock(ProyectoMiembroRepository.class);
    private final ProyectoItemRepository items = mock(ProyectoItemRepository.class);
    private final UserService userService = mock(UserService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AreaRepository areaRepository = mock(AreaRepository.class);
    private final NotificationService notificaciones = mock(NotificationService.class);
    private ProyectoService servicio;

    @BeforeEach
    void setUp() {
        servicio = new ProyectoService(proyectos, areasProyecto, miembros, mock(ProyectoSprintRepository.class), items,
                mock(ProyectoObjetivoRepository.class), userService, userRepository, areaRepository,
                new JefaturaService(userRepository), notificaciones, mock(SystemAuditService.class));
        when(areaRepository.findById(any())).thenAnswer(inv -> Optional.of(area(inv.getArgument(0))));
        when(areaRepository.findAll()).thenReturn(List.of(area(TECNOLOGIA), area(CONTABILIDAD)));
        when(proyectos.save(any())).thenAnswer(inv -> {
            Proyecto p = inv.getArgument(0);
            if (p.getIdProyecto() == null) p.setIdProyecto(PROYECTO);
            return p;
        });
    }

    private static Area area(UUID id) {
        Area a = new Area();
        a.setIdArea(id);
        a.setNameArea(id.equals(TECNOLOGIA) ? "Tecnología" : "Contabilidad");
        a.setDeletedArea(false);
        return a;
    }

    private static User persona(int n, String rol, UUID area) {
        User u = new User();
        u.setIdUser(id(100 + n));
        u.setCorporateEmailUser("p" + n + "@coac");
        Role r = new Role();
        r.setNameRole(rol);
        u.setRolUser(r);
        u.setArea(area(area));
        u.setDeletedUser(false);
        return u;
    }

    private Proyecto proyectoDeTecnologia() {
        Proyecto p = new Proyecto();
        p.setIdProyecto(PROYECTO);
        p.setNombre("Migración");
        p.setAreaId(TECNOLOGIA);
        p.setEstado("EN_CURSO");
        when(proyectos.findById(PROYECTO)).thenReturn(Optional.of(p));
        return p;
    }

    private static ProyectoItem item(String estado, int peso) {
        ProyectoItem i = new ProyectoItem();
        i.setEstado(estado);
        i.setPeso(peso);
        return i;
    }

    @Test
    @DisplayName("el avance pondera por peso: solo cuentan las tarjetas HECHO")
    void avancePonderado() {
        assertEquals(0, ProyectoService.avance(List.of()));
        assertEquals(75, ProyectoService.avance(List.of(item("HECHO", 3), item("EN_CURSO", 1))));
        assertEquals(25, ProyectoService.avance(List.of(item("POR_HACER", 3), item("HECHO", 1))));
        // El del proyecto suma el peso hecho: una tarjeta de 40 terminada = 40 %.
        assertEquals(40, ProyectoService.avanceProyecto(List.of(item("HECHO", 40))));
        assertEquals(40, ProyectoService.avanceProyecto(List.of(item("HECHO", 40), item("EN_CURSO", 60))));
        assertEquals(0, ProyectoService.avanceProyecto(List.of()));
    }

    @Test
    @DisplayName("el jefe crea proyectos de su área aunque pida otra; un agente no crea")
    void soloJefeDeSuArea() {
        User jefe = persona(1, "AREA_MANAGER", TECNOLOGIA);
        when(userService.getCurrentUser()).thenReturn(jefe);
        ProyectoService.Resumen r = servicio.crear(new ProyectoService.ProyectoRequest(
                "Migración", null, CONTABILIDAD, null, null, null, null, null));
        assertEquals(TECNOLOGIA, r.areaId());

        when(userService.getCurrentUser()).thenReturn(persona(2, "AGENT", TECNOLOGIA));
        assertThrows(AccessDeniedException.class, () -> servicio.crear(new ProyectoService.ProyectoRequest(
                "X", null, null, null, null, null, null, null)));
    }

    @Test
    @DisplayName("invitar a otra área avisa a su jefe; solo el gestor invita")
    void invitarAvisaAlOtroJefe() {
        proyectoDeTecnologia();
        User jefeTec = persona(1, "AREA_MANAGER", TECNOLOGIA);
        User jefeConta = persona(3, "AREA_MANAGER", CONTABILIDAD);
        when(userRepository.jefesActivosDe(CONTABILIDAD)).thenReturn(List.of(jefeConta));
        when(areasProyecto.findByProyectoIdAndAreaId(PROYECTO, CONTABILIDAD)).thenReturn(Optional.empty());

        when(userService.getCurrentUser()).thenReturn(persona(2, "AGENT", TECNOLOGIA));
        assertThrows(AccessDeniedException.class, () -> servicio.invitarArea(PROYECTO, CONTABILIDAD));

        when(userService.getCurrentUser()).thenReturn(jefeTec);
        servicio.invitarArea(PROYECTO, CONTABILIDAD);
        verify(notificaciones).notifyProyecto(eq(jefeConta), eq("PROYECTO_INVITACION"), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("solo el jefe del área invitada acepta; ni el dueño ni el súper admin lo hacen por él")
    void soloElJefeInvitadoDecide() {
        proyectoDeTecnologia();
        ProyectoArea pa = new ProyectoArea();
        pa.setProyectoId(PROYECTO);
        pa.setAreaId(CONTABILIDAD);
        pa.setEstado("PENDIENTE");
        when(areasProyecto.findByProyectoIdAndAreaId(PROYECTO, CONTABILIDAD)).thenReturn(Optional.of(pa));

        when(userService.getCurrentUser()).thenReturn(persona(1, "AREA_MANAGER", TECNOLOGIA));
        assertThrows(AccessDeniedException.class, () -> servicio.decidir(PROYECTO, CONTABILIDAD,
                new ProyectoService.DecisionRequest(true, null)));
        when(userService.getCurrentUser()).thenReturn(persona(9, "SUPER_ADMIN", TECNOLOGIA));
        assertThrows(AccessDeniedException.class, () -> servicio.decidir(PROYECTO, CONTABILIDAD,
                new ProyectoService.DecisionRequest(true, null)));

        when(userService.getCurrentUser()).thenReturn(persona(3, "AREA_MANAGER", CONTABILIDAD));
        servicio.decidir(PROYECTO, CONTABILIDAD, new ProyectoService.DecisionRequest(true, null));
        assertEquals("ACEPTADA", pa.getEstado());
    }

    @Test
    @DisplayName("al equipo solo entra gente del área dueña o de áreas que aceptaron")
    void miembrosSoloDeAreasDelProyecto() {
        proyectoDeTecnologia();
        when(userService.getCurrentUser()).thenReturn(persona(1, "AREA_MANAGER", TECNOLOGIA));
        when(areasProyecto.findByProyectoIdAndAreaId(any(), any())).thenReturn(Optional.empty());
        when(areasProyecto.findByProyectoIdOrderByCreatedAtAsc(PROYECTO)).thenReturn(List.of());
        User contador = persona(4, "USER", CONTABILIDAD);
        when(userRepository.findById(contador.getIdUser())).thenReturn(Optional.of(contador));

        assertThrows(AccessDeniedException.class, () -> servicio.agregarMiembro(PROYECTO,
                new ProyectoService.MiembroRequest(contador.getIdUser(), null)));
        verify(miembros, never()).save(any());
    }

    @Test
    @DisplayName("los pesos de las tarjetas no pasan de 100% entre todas")
    void pesosHastaCien() {
        proyectoDeTecnologia();
        when(userService.getCurrentUser()).thenReturn(persona(1, "AREA_MANAGER", TECNOLOGIA));
        ProyectoItem existente = item("POR_HACER", 100);
        existente.setIdProyectoItem(id(70));
        when(items.findByProyectoIdAndDeletedFalseOrderByOrdenAscCreatedAtAsc(PROYECTO)).thenReturn(List.of(existente));

        var e = assertThrows(com.mesaayuda.exception.BadRequestException.class, () -> servicio.crearItem(PROYECTO,
                new ProyectoService.ItemRequest("Otra", null, null, 100, null, null, null)));
        assertTrue(e.getMessage().contains("100%"));
        verify(items, never()).save(any());
    }

    @Test
    @DisplayName("la fecha límite de una tarjeta cae dentro del plazo del proyecto")
    void fechaDentroDelProyecto() {
        Proyecto p = proyectoDeTecnologia();
        p.setInicio(LocalDate.of(2026, 11, 1));
        p.setFin(LocalDate.of(2026, 11, 30));
        when(userService.getCurrentUser()).thenReturn(persona(1, "AREA_MANAGER", TECNOLOGIA));
        when(items.findByProyectoIdAndDeletedFalseOrderByOrdenAscCreatedAtAsc(PROYECTO)).thenReturn(List.of());

        assertThrows(com.mesaayuda.exception.BadRequestException.class, () -> servicio.crearItem(PROYECTO,
                new ProyectoService.ItemRequest("Tarde", null, null, 10, null, LocalDate.of(2026, 12, 5), null)));
    }
}
