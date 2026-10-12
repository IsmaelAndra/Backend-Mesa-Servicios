package com.mesaayuda.workflow;

import java.util.UUID;
import static com.mesaayuda.IdsPrueba.id;
import com.mesaayuda.entity.Role;
import com.mesaayuda.entity.User;
import com.mesaayuda.entity.Workflow;
import com.mesaayuda.entity.WorkflowVersion;
import com.mesaayuda.exception.BadRequestException;
import com.mesaayuda.exception.ResourceNotFoundException;
import com.mesaayuda.repository.WorkflowRepository;
import com.mesaayuda.repository.WorkflowVersionRepository;
import com.mesaayuda.service.SystemAuditService;
import com.mesaayuda.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Versionado de workflows.
 *
 * <p>Aquí está el compromiso central del motor: editar un proceso no puede alterar
 * lo que hacen las automatizaciones que ya están corriendo. Las versiones son
 * inmutables al publicar y cada ejecución guarda con cuál arrancó.
 *
 * <p>Si este servicio permitiera editar la definición vigente, un workflow que
 * lleva tres meses en producción empezaría a hacer cosas nuevas sin que nadie lo
 * pidiera, y el historial de ejecuciones mentiría al decir qué versión se usó.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkflowServiceVersionadoTest {

@Mock private WorkflowRepository workflowRepository;
    @Mock private WorkflowVersionRepository versionRepository;
    @Mock private UserService userService;
    @Mock private SystemAuditService systemAuditService;

    @InjectMocks private WorkflowService service;

    private User superAdmin;

    @BeforeEach
    void setUp() {
        Role rol = new Role();
        rol.setNameRole("SUPER_ADMIN");
        superAdmin = new User();
        superAdmin.setIdUser(id(1));
        superAdmin.setCorporateEmailUser("root@coacmagisterio.fin.ec");
        // El rol no es opcional: el servicio deriva de él el ámbito con el que se
        // ejecutará el workflow, y sin él no sabe qué permisos darle.
        superAdmin.setRolUser(rol);
        when(userService.getCurrentUser()).thenReturn(superAdmin);
        // Un ObjectMapper de verdad. Con un mock, writeValueAsString devolvería
        // null y reventaría al medir la longitud de la definición.
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
        // El validador real, no un mock: publicar una version debe VALIDAR de verdad.
        // Con un mock pasaria cualquier grafo roto y el fallo apareceria tres dias
        // despues, que es justo lo que esta etapa quiere evitar.
        ReflectionTestUtils.setField(service, "validation", new WorkflowValidationService());
    }

    private Workflow existente() {
        Workflow w = new Workflow();
        w.setIdWorkflow(id(10));
        w.setNameWorkflow("Escalamiento SLA");
        w.setStatusWorkflow(Workflow.ESTADO_ACTIVO);
        w.setCurrentVersion(2);
        w.setCreatedByWorkflow(id(1));
        w.setAuthorityScope("SUPER_ADMIN");
        return w;
    }

    private WorkflowVersion version(int numero, boolean vigente) {
        WorkflowVersion v = new WorkflowVersion();
        v.setIdWorkflowVersion(id(numero * 100L));
        v.setWorkflowWorkflow(id(10));
        v.setVersionWorkflow(numero);
        v.setIsActiveVersion(vigente);
        v.setDefinitionWorkflow("{\"nodes\":[]}");
        v.setCreatedByWorkflow(id(1));
        v.setCreatedAtWorkflow(OffsetDateTime.now());
        if (vigente) {
            v.setPublishedAtWorkflow(OffsetDateTime.now());
        }
        return v;
    }

    // ============================ Alta ============================

    @Test
    @DisplayName("crear un workflow parte en BORRADOR y no se ejecuta aunque tenga trigger")
    void crearParteEnBorrador() {
        when(workflowRepository.save(any(Workflow.class)))
                .thenAnswer(inv -> {
                    Workflow w = inv.getArgument(0);
                    w.setIdWorkflow(id(10));
                    return w;
                });

        Workflow creado = service.crear("Escalamiento SLA", "Criticos a infraestructura",
                "TICKET_CREATED", null);

        assertEquals(Workflow.ESTADO_BORRADOR, creado.getStatusWorkflow(),
                "un workflow nuevo no debe ejecutarse: nadie lo ha revisado");
        org.junit.jupiter.api.Assertions.assertNotNull(creado.getCreatedByWorkflow());
        assertEquals(0L, creado.getRunCountWorkflow());
        assertFalse(creado.estaActivo());
    }

    @Test
    @DisplayName("crear un workflow audita")
    void crearAudita() {
// El stub devuelve la entidad CON id, como haria la base de datos: la
        // auditoria necesita el id para poder localizar despues la fila.
        when(workflowRepository.save(any(Workflow.class))).thenAnswer(inv -> {
            Workflow w = inv.getArgument(0);
            w.setIdWorkflow(id(10));
            return w;
        });

        service.crear("Nuevo", null, "TICKET_CREATED", null);

        verify(systemAuditService).record(eq("WORKFLOW_CREATE"), eq("Workflow"), eq(id(10)),
                eq("root@coacmagisterio.fin.ec"), anyString());
    }

    @Test
    @DisplayName("el nombre del workflow es obligatorio")
    void nombreObligatorio() {
        assertThrows(BadRequestException.class,
                () -> service.crear("  ", null, "TICKET_CREATED", null));
    }

    @Test
    @DisplayName("no se admite un tipo de disparador vacio")
    void triggerObligatorio() {
        assertThrows(BadRequestException.class,
                () -> service.crear("X", null, null, null));
    }

    @Test
    @DisplayName("no se pueden crear dos workflows vivos con el mismo nombre")
    void nombreUnico() {
        when(workflowRepository.findByNameWorkflowAndDeletedWorkflowFalse("Escalamiento"))
                .thenReturn(Optional.of(existente()));

        // El nombre identifica el proceso para quien lo lee. Dos en papel distinto
        // peleandose por el mismo nombre solo crean confusion a la hora de buscar
        // cual se disparo.
        assertThrows(BadRequestException.class,
                () -> service.crear("Escalamiento", null, "TICKET_CREATED", null));
    }

    // ============================ Versionado ============================

    @Test
    @DisplayName("publicar la primera version crea la v1 y la marca vigente")
    void publicarPrimeraVersion() {
        Workflow w = existente();
        w.setCurrentVersion(0);
        when(workflowRepository.findById(id(10))).thenReturn(Optional.of(w));
        when(workflowRepository.findByNameWorkflowAndDeletedWorkflowFalse(anyString()))
                .thenReturn(Optional.empty());
        when(versionRepository.findFirstByWorkflowWorkflowOrderByVersionWorkflowDesc(id(10)))
                .thenReturn(Optional.empty());
        when(versionRepository.save(any(WorkflowVersion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(workflowRepository.save(any(Workflow.class))).thenAnswer(inv -> inv.getArgument(0));

        WorkflowVersion publicada = service.publicarVersion(id(10), grafoValido());

        assertEquals(1, publicada.getVersionWorkflow());
        assertTrue(publicada.getIsActiveVersion());
        org.junit.jupiter.api.Assertions.assertNotNull(publicada.getPublishedAtWorkflow());
    }

    @Test
    @DisplayName("modificar un workflow activo crea una version nueva, no toca la anterior")
    void editarActivoCreaVersionNueva() {
        // Este es el compromiso central. Si se editara la version vigente en
        // sitio, las ejecuciones en vuelo cambiarian de reglas bajo los pies.
        Workflow w = existente();
        when(workflowRepository.findById(id(10))).thenReturn(Optional.of(w));
        when(workflowRepository.findByNameWorkflowAndDeletedWorkflowFalse(anyString()))
                .thenReturn(Optional.empty());
        when(versionRepository.findFirstByWorkflowWorkflowOrderByVersionWorkflowDesc(id(10)))
                .thenReturn(Optional.of(version(2, true)));
        when(versionRepository.findByWorkflowWorkflowAndIsActiveVersionTrue(id(10)))
                .thenReturn(Optional.of(version(2, true)));
        when(versionRepository.save(any(WorkflowVersion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(workflowRepository.save(any(Workflow.class))).thenAnswer(inv -> inv.getArgument(0));

        WorkflowVersion nueva = service.publicarVersion(id(10), grafoValido());

        assertEquals(3, nueva.getVersionWorkflow(), "la nueva debe ser la 3");
        assertTrue(nueva.getIsActiveVersion());
        assertEquals(3, w.getCurrentVersion());

        // Y la version 2 no se borra ni se modifica: se desactiva.
        verify(versionRepository, times(2)).save(any(WorkflowVersion.class));
        verify(versionRepository, never()).delete(any());
    }

    @Test
    @DisplayName("publicar una version desactiva la anterior sin borrarla")
    void desactivarLaAnteriorSinBorrarla() {
        Workflow w = existente();
        when(workflowRepository.findById(id(10))).thenReturn(Optional.of(w));
        when(workflowRepository.findByNameWorkflowAndDeletedWorkflowFalse(anyString()))
                .thenReturn(Optional.empty());
        when(versionRepository.findFirstByWorkflowWorkflowOrderByVersionWorkflowDesc(id(10)))
                .thenReturn(Optional.of(version(2, true)));
        WorkflowVersion vigente = version(2, true);
        when(versionRepository.findByWorkflowWorkflowAndIsActiveVersionTrue(id(10)))
                .thenReturn(Optional.of(vigente));
        when(versionRepository.save(any(WorkflowVersion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(workflowRepository.save(any(Workflow.class))).thenAnswer(inv -> inv.getArgument(0));

        service.publicarVersion(id(10), grafoValido());

        // Sigue existiendo, pero ya no es la vigente. Es lo que permite que una
        // ejecucion a medio camino siga terminando con las reglas con las que
        // empezo.
        assertFalse(vigente.getIsActiveVersion());
        verify(versionRepository, never()).delete(any());
    }

    @Test
    @DisplayName("no se publica un grafo invalido")
    void noSePublicaGrafoInvalido() {
        Workflow w = existente();
        w.setStatusWorkflow(Workflow.ESTADO_BORRADOR);
        when(workflowRepository.findById(id(10))).thenReturn(Optional.of(w));

        WorkflowGraphDef roto = new WorkflowGraphDef("x",
                List.of(new WorkflowNodeDef("ini", "INICIO", "i", Map.of(), 0.0, 0.0)),
                List.of(), Map.of());

        assertThrows(BadRequestException.class, () -> service.publicarVersion(id(10), roto));

        verify(versionRepository, never()).save(any());
    }

    @Test
    @DisplayName("un workflow inexistente da 404")
    void inexistenteDa404() {
        when(workflowRepository.findById(id(999))).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.publicarVersion(id(999), grafoValido()));
    }

    // ============================ Activacion ============================

    @Test
    @DisplayName("activar un borrador lo pasa a ACTIVO")
    void activarBorrador() {
        Workflow w = existente();
        w.setStatusWorkflow(Workflow.ESTADO_BORRADOR);
        when(workflowRepository.findById(id(10))).thenReturn(Optional.of(w));
        when(versionRepository.findByWorkflowWorkflowAndIsActiveVersionTrue(id(10)))
                .thenReturn(Optional.of(version(1, true)));
        when(workflowRepository.save(any(Workflow.class))).thenAnswer(inv -> inv.getArgument(0));

        service.activar(id(10));

        assertTrue(w.estaActivo());
        verify(systemAuditService).record(eq("WORKFLOW_ACTIVATE"), eq("Workflow"), eq(id(10)),
                eq("root@coacmagisterio.fin.ec"), anyString());
    }

    @Test
    @DisplayName("no se puede activar un workflow sin version vigente")
    void activarSinVersionVigente() {
        // Es la trampa facil: activar un borrador que nunca se publico. Se
        // aceptaria y el motor se encontraria sin grafo que ejecutar.
        Workflow w = existente();
        w.setStatusWorkflow(Workflow.ESTADO_BORRADOR);
        when(workflowRepository.findById(id(10))).thenReturn(Optional.of(w));
        when(versionRepository.findByWorkflowWorkflowAndIsActiveVersionTrue(id(10)))
                .thenReturn(Optional.empty());

        assertThrows(BadRequestException.class, () -> service.activar(id(10)));
        verify(workflowRepository, never()).save(any());
    }

    @Test
    @DisplayName("desactivar lo deja en INACTIVO y conserva la version")
    void desactivar() {
        Workflow w = existente();
        when(workflowRepository.findById(id(10))).thenReturn(Optional.of(w));
        when(workflowRepository.save(any(Workflow.class))).thenAnswer(inv -> inv.getArgument(0));

        service.desactivar(id(10));

        // INACTIVO y no BORRADOR: conserva la version publicada, de modo que al
        // reactivarlo sigue haciendo lo mismo.
        assertEquals(Workflow.ESTADO_INACTIVO, w.getStatusWorkflow());
        verify(versionRepository, never()).delete(any());
    }

    // ============================ Authority scope ============================

    @Test
    @DisplayName("el alcance de autoridad se deriva del autor, nunca de lo que llegue")
    void alcanceSeDerivaDelAutor() {
        when(workflowRepository.save(any(Workflow.class))).thenAnswer(inv -> inv.getArgument(0));

        Workflow creado = service.crear("X", null, "TICKET_CREATED", null);

        // Es lo que impide que un workflow ejecute con mas permisos de los que
        // tenia quien lo creo.
        assertEquals("SUPER_ADMIN", creado.getAuthorityScope(),
                "un SUPER_ADMIN crea workflows que ejecutan como SUPER_ADMIN");
    }

    @Test
    @DisplayName("borrar es logico: las ejecuciones historicas siguen siendo legibles")
    void borrarEsLogico() {
        Workflow w = existente();
        when(workflowRepository.findById(id(10))).thenReturn(Optional.of(w));
        when(workflowRepository.save(any(Workflow.class))).thenAnswer(inv -> inv.getArgument(0));

        service.borrar(id(10));

        assertTrue(creadoEstaBorrado(w), "debe marcarse borrado, no desaparecer");
        // No se borra en cascada nada: el historial tiene que seguir disponible
        // para poder explicar que hizo un workflow hace seis meses.
        verify(versionRepository, never()).delete(any());
    }

    private boolean creadoEstaBorrado(Workflow w) {
        return Boolean.TRUE.equals(w.getDeletedWorkflow());
    }

    // ============================ helpers ============================

    private WorkflowGraphDef grafoValido() {
        return new WorkflowGraphDef("g",
                List.of(
                        new WorkflowNodeDef("ini", "INICIO", "Inicio", Map.of(), 0.0, 0.0),
                        new WorkflowNodeDef("aviso", "NOTIFY", "Avisar", Map.of("mensaje", "Aviso", "destino", "AGENTE"), 100.0, 0.0),
                        new WorkflowNodeDef("fin", "FIN", "Fin", Map.of(), 200.0, 0.0)),
                List.of(
                        new WorkflowEdgeDef("ini", "aviso", "NEXT", null),
                        new WorkflowEdgeDef("aviso", "fin", "NEXT", null)),
                Map.of());
    }

    private org.mockito.ArgumentCaptor<WorkflowVersion> argThatVersionNumero(int numero) {
        return ArgumentCaptor.forClass(WorkflowVersion.class);
    }

    private void assertNotNull(Object o) {
        org.junit.jupiter.api.Assertions.assertNotNull(o);
    }
}