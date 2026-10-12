package com.mesaayuda.controller;

import static com.mesaayuda.IdsPrueba.id;
import com.mesaayuda.dto.WorkflowCreateRequest;
import com.mesaayuda.dto.WorkflowDetailResponse;
import com.mesaayuda.dto.WorkflowSummaryResponse;
import com.mesaayuda.entity.Workflow;
import com.mesaayuda.entity.WorkflowVersion;
import com.mesaayuda.workflow.WorkflowGraphDef;
import com.mesaayuda.workflow.WorkflowNodeDef;
import com.mesaayuda.workflow.WorkflowExecutionService;
import com.mesaayuda.workflow.WorkflowService;
import com.mesaayuda.workflow.WorkflowTriggerService;
import com.mesaayuda.workflow.WorkflowValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Gestión de workflows por HTTP.
 *
 * <p>Aquí hay dos cosas distintas y conviene no mezclarlas.
 *
 * <p>La primera es <b>quién puede entrar</b>. Eso lo decide el filtro de URLs de
 * la seguridad: {@code /admin/**} pide ser ADMIN o SUPER_ADMIN, y el
 * {@code @PreAuthorize} de la clase estrecha el margen a solo SUPER_ADMIN. La
 * anotación se comprueba por reflexión, sin arrancar Spring, porque lo que
 * importa es que no desaparezca por un refactor.
 *
 * <p>La segunda es <b>qué hace el controlador</b>: a qué método del servicio
 * llama, con qué estado responde y qué devuelve. Eso sí se prueba invocándolo
 * directamente.
 *
 * <p>Que sea solo SUPER_ADMIN no es un capricho. Programar una automatización es
 * escribir código con los permisos de otro: quien la programa elige qué tickets
 * se mueven, a quién se le escribe y con qué datos. Si un ADMIN pudiera hacerlo,
 * podría dejar procesos operando por debajo de la mesa sin que nadie lo autorice.
 */
class WorkflowControllerAutorizacionTest {

private WorkflowService service;
    private WorkflowExecutionService executionService;
    private WorkflowTriggerService triggerService;
    private WorkflowValidationService validation;
    private WorkflowController controller;
    private com.mesaayuda.repository.TicketRepository ticketRepository;
    private com.mesaayuda.repository.UserRepository userRepository;

    @BeforeEach
    void setUp() {
        service = mock(WorkflowService.class);
        executionService = mock(WorkflowExecutionService.class);
        triggerService = mock(WorkflowTriggerService.class);
        validation = new WorkflowValidationService();
        controller = new WorkflowController(service, executionService, triggerService, validation,
                mock(com.mesaayuda.service.UserService.class), mock(com.mesaayuda.service.SystemAuditService.class),
                ticketRepository = mock(com.mesaayuda.repository.TicketRepository.class),
                userRepository = mock(com.mesaayuda.repository.UserRepository.class));
    }

    // ============================ Autorizacion ============================

    @Test
    @DisplayName("el controlador entero exige ROLE_SUPER_ADMIN, no solo ADMIN")
    void exigeSuperAdmin() {
        PreAuthorize anotacion = WorkflowController.class.getAnnotation(PreAuthorize.class);

        assertNotNull(anotacion,
                "sin @PreAuthorize de clase, un ADMIN pasaria el filtro /admin/** y "
                        + "podria crear automatizaciones");
        assertEquals("hasAuthority('ROLE_SUPER_ADMIN')", anotacion.value());
        assertTrue(!anotacion.value().contains("ROLE_ADMIN"),
                "el permiso no puede incluir a ADMIN junto a SUPER_ADMIN");
    }

    @Test
    @DisplayName("ningun metodo individual relaja el permiso heredado")
    void ningunMetodoRelajaElPermiso() {
        // Un @PreAuthorize de metodo con otro valor sobreescribiria el de clase y
        // abriria un endpoint concreto sin querer.
        for (Method m : WorkflowController.class.getDeclaredMethods()) {
            PreAuthorize p = m.getAnnotation(PreAuthorize.class);
            if (p != null) {
                assertEquals("hasAuthority('ROLE_SUPER_ADMIN')", p.value(),
                        "el metodo " + m.getName() + " relaja el permiso de la clase");
            }
        }
    }

    // ============================ Delegacion ============================

    @Test
    @DisplayName("el listado se apoya en el servicio y devuelve 200")
    void listado() {
        when(service.listar()).thenReturn(List.of(ejemplo()));

        ResponseEntity<List<WorkflowSummaryResponse>> respuesta = controller.listar();

        assertEquals(HttpStatus.OK, respuesta.getStatusCode());
        assertEquals(1, respuesta.getBody().size());
        assertEquals("Escalamiento SLA", respuesta.getBody().get(0).name());
    }

    @Test
    @DisplayName("crear responde 201 y delega con los datos del cuerpo")
    void crear() {
        when(service.crear(eq("Escalamiento"), eq("Criticos"), eq("TICKET_CREATED"), eq("{}")))
                .thenReturn(ejemplo());

        ResponseEntity<com.mesaayuda.dto.WorkflowSummaryResponse> respuesta = controller.crear(
                new com.mesaayuda.dto.WorkflowCreateRequest(
                        "Escalamiento", "Criticos", "TICKET_CREATED", "{}"));

        assertEquals(HttpStatus.CREATED, respuesta.getStatusCode());
        verify(service).crear("Escalamiento", "Criticos", "TICKET_CREATED", "{}");
    }

    @Test
    @DisplayName("el controlador no acepta un ambito de autoridad desde el cuerpo")
    void noAceptaAmbitoDesdeElCuerpo() {
        // La anotacion de permiso es la unica defensa. Si en algun momento se
        // anadiera un campo authorityScope al request, el usuario podria escribir
        // a mano el ambito con el que se ejecutara su proceso.
        RecordComponent[] campos = WorkflowCreateRequest.class.getRecordComponents();
        assertEquals(List.of("name", "description", "triggerType", "triggerConfig"),
                java.util.Arrays.stream(campos).map(c -> c.getName()).toList(),
                "el payload de alta no debe llevar ningun campo de permisos");
    }

    @Test
    @DisplayName("publicar delega el grafo y responde 201")
    void publicar() {
        WorkflowGraphDef grafo = grafoMinimo();
        WorkflowVersion guardada = new WorkflowVersion();
        guardada.setIdWorkflowVersion(id(200));
        guardada.setVersionWorkflow(3);
        guardada.setIsActiveVersion(true);
        when(service.publicarVersion(eq(id(10)), any(WorkflowGraphDef.class))).thenReturn(guardada);

        ResponseEntity<WorkflowDetailResponse.VersionResponse> r =
                controller.publicar(id(10), grafo);

        assertEquals(HttpStatus.CREATED, r.getStatusCode());
        assertEquals(3, r.getBody().version());
        verify(service).publicarVersion(id(10), grafo);
    }

    @Test
    @DisplayName("activar y desactivar responden 204 sin cuerpo")
    void activarYDesactivar() {
        assertEquals(HttpStatus.NO_CONTENT, controller.activar(id(10)).getStatusCode());
        assertEquals(HttpStatus.NO_CONTENT, controller.desactivar(id(10)).getStatusCode());

        verify(service).activar(id(10));
        verify(service).desactivar(id(10));
    }

    @Test
    @DisplayName("borrar responde 204 y avisa al servicio una sola vez")
    void borrar() {
        assertEquals(HttpStatus.NO_CONTENT, controller.borrar(id(10)).getStatusCode());

        verify(service).borrar(id(10));
    }

    @Test
    @DisplayName("el detalle pide las versiones al servicio")
    void detalle() {
        when(service.obtener(id(10))).thenReturn(ejemplo());
        when(service.versiones(id(10))).thenReturn(List.of());

        ResponseEntity<WorkflowDetailResponse> r = controller.detalle(id(10));

        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertEquals("SUPER_ADMIN", r.getBody().authorityScope());
        assertEquals(List.of(), r.getBody().versions());
        verify(service).versiones(id(10));
    }

    // ============================ helpers ============================

    private Workflow ejemplo() {
        Workflow w = new Workflow();
        w.setIdWorkflow(id(10));
        w.setNameWorkflow("Escalamiento SLA");
        w.setDescriptionWorkflow("Criticos a infraestructura");
        w.setStatusWorkflow(Workflow.ESTADO_ACTIVO);
        w.setTriggerType("TICKET_CREATED");
        w.setCurrentVersion(2);
        w.setRunCountWorkflow(7L);
        w.setAuthorityScope("SUPER_ADMIN");
        return w;
    }

    private WorkflowGraphDef grafoMinimo() {
        return new WorkflowGraphDef("g",
                List.of(
                        new WorkflowNodeDef("ini", "INICIO", "Inicio", Map.of(), 0.0, 0.0),
                        new WorkflowNodeDef("fin", "FIN", "Fin", Map.of(), 200.0, 0.0)),
                List.of(),
                Map.of());
    }

    @Test
    @DisplayName("al probar, el número del ticket (#43) y el correo del usuario se traducen a su uuid")
    void probarConNumeroDeTicket() {
        com.mesaayuda.entity.Ticket t = new com.mesaayuda.entity.Ticket();
        t.setIdTicket(java.util.UUID.fromString("00000000-0000-0000-0000-00000000002b"));
        when(ticketRepository.findByNumberTicketAndDeletedTicketFalse(43L)).thenReturn(java.util.Optional.of(t));

        assertEquals(t.getIdTicket(), controller.conEntidadResuelta(Map.of("ticketId", "#43")).get("ticketId"));
        assertEquals(t.getIdTicket(), controller.conEntidadResuelta(Map.of("ticketId", 43)).get("ticketId"));
        // Un uuid ya resuelto no se toca ni se consulta.
        Map<String, Object> conUuid = Map.of("ticketId", t.getIdTicket().toString());
        assertEquals(conUuid, controller.conEntidadResuelta(conUuid));

        com.mesaayuda.entity.User u = new com.mesaayuda.entity.User();
        u.setIdUser(java.util.UUID.fromString("00000000-0000-0000-0000-000000000007"));
        when(userRepository.findByCorporateEmailUser("ana@coac.ec")).thenReturn(java.util.Optional.of(u));
        assertEquals(u.getIdUser(), controller.conEntidadResuelta(Map.of("usuarioId", "ana@coac.ec")).get("usuarioId"));
        org.junit.jupiter.api.Assertions.assertThrows(com.mesaayuda.exception.BadRequestException.class,
                () -> controller.conEntidadResuelta(Map.of("ticketId", "999")));
    }
}
