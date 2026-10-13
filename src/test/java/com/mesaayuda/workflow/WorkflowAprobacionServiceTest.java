package com.mesaayuda.workflow;

import java.util.UUID;
import static com.mesaayuda.IdsPrueba.id;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mesaayuda.entity.Role;
import com.mesaayuda.entity.User;
import com.mesaayuda.entity.WorkflowExecution;
import com.mesaayuda.entity.WorkflowWait;
import com.mesaayuda.exception.BadRequestException;
import com.mesaayuda.repository.TicketRepository;
import com.mesaayuda.repository.UserRepository;
import com.mesaayuda.service.NotificationService;
import com.mesaayuda.repository.WorkflowExecutionRepository;
import com.mesaayuda.repository.WorkflowRepository;
import com.mesaayuda.repository.WorkflowWaitRepository;
import com.mesaayuda.service.SystemAuditService;
import com.mesaayuda.service.TicketAccessPolicy;
import com.mesaayuda.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Quién puede decidir una aprobación y qué pasa con la ejecución al decidir. */
class WorkflowAprobacionServiceTest {

    private WorkflowWaitRepository waits;
    private WorkflowExecutionRepository ejecuciones;
    private UserService usuarios;
    private WorkflowAprobacionService servicio;

    @BeforeEach
    void setUp() {
        waits = mock(WorkflowWaitRepository.class);
        ejecuciones = mock(WorkflowExecutionRepository.class);
        usuarios = mock(UserService.class);
        servicio = new WorkflowAprobacionService(waits, ejecuciones, mock(WorkflowRepository.class),
                mock(TicketRepository.class), usuarios, mock(TicketAccessPolicy.class),
                mock(SystemAuditService.class), new ObjectMapper(), mock(UserRepository.class),
                mock(NotificationService.class));
    }

    private static User usuario(UUID id, String rol) {
        Role r = new Role();
        r.setNameRole(rol);
        User u = new User();
        u.setIdUser(id);
        u.setRolUser(r);
        u.setStatusUser("ACTIVE");
        u.setDeletedUser(false);
        u.setCorporateEmailUser("u" + id + "@coac.test");
        return u;
    }

    private static WorkflowWait espera(String rol, UUID aprobador) {
        WorkflowWait w = new WorkflowWait();
        w.setIdWait(id(1));
        w.setExecutionExecution(id(500));
        w.setNodeIdWait("aprobar");
        w.setKindWait(WorkflowWait.TIPO_APPROVAL);
        w.setApproverRoleWait(rol);
        w.setApproverUserWait(aprobador);
        w.setStatusWait(WorkflowWait.ESTADO_PENDIENTE);
        return w;
    }

    @Test
    void soloDecideUnSuperAdmin() {
        WorkflowWait pideAdmin = espera("ADMIN", null);
        assertTrue(servicio.puedeDecidir(usuario(id(2), "SUPER_ADMIN"), pideAdmin));
        assertFalse(servicio.puedeDecidir(usuario(id(1), "ADMIN"), pideAdmin),
                "el ADMIN origina la solicitud y espera; no la decide");
        assertFalse(servicio.puedeDecidir(usuario(id(3), "AGENT"), pideAdmin));
        assertFalse(servicio.puedeDecidir(usuario(id(4), "USER"), pideAdmin));
    }

    @Test
    void conAprobadorConcretoSoloDecideEsaPersona() {
        WorkflowWait paraElSiete = espera("SUPER_ADMIN", id(7));
        assertTrue(servicio.puedeDecidir(usuario(id(7), "SUPER_ADMIN"), paraElSiete));
        assertFalse(servicio.puedeDecidir(usuario(id(8), "SUPER_ADMIN"), paraElSiete),
                "ni otro SUPER_ADMIN decide una aprobación asignada a otra persona");
    }

    @Test
    void unaCuentaDesactivadaNoDecide() {
        User baja = usuario(id(1), "SUPER_ADMIN");
        baja.setStatusUser("INACTIVE");
        assertFalse(servicio.puedeDecidir(baja, espera("SUPER_ADMIN", null)));
    }

    @Test
    void elAdminVeSusSolicitudesConLaDecision() {
        User admin = usuario(id(1), "ADMIN");
        when(usuarios.getCurrentUser()).thenReturn(admin);
        WorkflowWait suya = espera("SUPER_ADMIN", null);
        suya.setStatusWait(WorkflowWait.ESTADO_RECHAZADA);
        suya.setCommentWait("Falta el presupuesto");
        WorkflowWait ajena = espera("SUPER_ADMIN", null);
        ajena.setIdWait(id(2));
        ajena.setExecutionExecution(id(501));
        when(waits.aprobacionesRecientes(any())).thenReturn(java.util.List.of(suya, ajena));
        WorkflowExecution e1 = new WorkflowExecution();
        e1.setVariablesExecution("{\"evento\":{\"actorCorreo\":\"" + admin.getCorporateEmailUser() + "\"}}");
        WorkflowExecution e2 = new WorkflowExecution();
        e2.setVariablesExecution("{\"evento\":{\"actorCorreo\":\"otro@coac.test\"}}");
        when(ejecuciones.findById(id(500))).thenReturn(Optional.of(e1));
        when(ejecuciones.findById(id(501))).thenReturn(Optional.of(e2));

        var mias = servicio.misSolicitudes();

        assertEquals(1, mias.size());
        assertEquals(WorkflowWait.ESTADO_RECHAZADA, mias.get(0).status());
        assertEquals("Falta el presupuesto", mias.get(0).comment());
    }

    @Test
    void aprobarDevuelveLaEjecucionALaColaPorLaRamaAprobado() {
        when(usuarios.getCurrentUser()).thenReturn(usuario(id(1), "SUPER_ADMIN"));
        when(waits.findById(id(1))).thenReturn(Optional.of(espera("SUPER_ADMIN", null)));
        when(waits.decidir(eq(id(1)), anyString(), any(java.util.UUID.class), any(), any())).thenReturn(1);
        WorkflowExecution e = new WorkflowExecution();
        e.setIdWorkflowExecution(id(500));
        e.setStatusExecution(WorkflowExecution.ESTADO_APROBACION);
        e.setVariablesExecution("{\"ticketId\":77}");
        when(ejecuciones.findById(id(500))).thenReturn(Optional.of(e));

        servicio.decidir(id(1), true, "Adelante");

        assertEquals(WorkflowExecution.ESTADO_PENDIENTE, e.getStatusExecution());
        assertEquals(WorkflowExecution.PUERTO_APROBADO, e.getResumePortExecution());
        assertTrue(e.getVariablesExecution().contains("\"decision\":\"APROBADO\""), e.getVariablesExecution());
        assertTrue(e.getVariablesExecution().contains("\"ticketId\":77"), "no se pierden las demás variables");
    }

    @Test
    void sinPermisoSeDeniegaYNoSeTocaNada() {
        when(usuarios.getCurrentUser()).thenReturn(usuario(id(3), "ADMIN"));
        when(waits.findById(id(1))).thenReturn(Optional.of(espera("SUPER_ADMIN", null)));

        assertThrows(AccessDeniedException.class, () -> servicio.decidir(id(1), true, null));
        verify(waits, never()).decidir(any(java.util.UUID.class), anyString(), any(java.util.UUID.class), any(), any());
    }

    @Test
    void rechazarExigeMotivo() {
        when(usuarios.getCurrentUser()).thenReturn(usuario(id(1), "SUPER_ADMIN"));
        when(waits.findById(id(1))).thenReturn(Optional.of(espera("SUPER_ADMIN", null)));

        assertThrows(BadRequestException.class, () -> servicio.decidir(id(1), false, "  "));
    }

    @Test
    void siOtraPersonaYaDecidioNoSeDecideDosVeces() {
        when(usuarios.getCurrentUser()).thenReturn(usuario(id(1), "SUPER_ADMIN"));
        when(waits.findById(id(1))).thenReturn(Optional.of(espera("SUPER_ADMIN", null)));
        when(waits.decidir(eq(id(1)), anyString(), any(java.util.UUID.class), any(), any())).thenReturn(0);

        assertThrows(BadRequestException.class, () -> servicio.decidir(id(1), true, null));
        verify(ejecuciones, never()).save(any());
    }
}
