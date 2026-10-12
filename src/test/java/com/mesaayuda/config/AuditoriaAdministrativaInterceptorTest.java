package com.mesaayuda.config;

import static com.mesaayuda.IdsPrueba.id;
import com.mesaayuda.service.SystemAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Auditoría automática de los cambios hechos por /admin. */
class AuditoriaAdministrativaInterceptorTest {

    private SystemAuditService auditoria;
    private AuditoriaAdministrativaInterceptor interceptor;

    @BeforeEach
    void setUp() {
        auditoria = mock(SystemAuditService.class);
        interceptor = new AuditoriaAdministrativaInterceptor(auditoria);
    }

    private void completar(String metodo, String uri, int estado) {
        MockHttpServletRequest req = new MockHttpServletRequest(metodo, "/api" + uri);
        req.setContextPath("/api");
        MockHttpServletResponse res = new MockHttpServletResponse();
        res.setStatus(estado);
        interceptor.afterCompletion(req, res, new Object(), null);
    }

    @Test
    void cambioDeSlaQuedaAuditado() {
        completar("PUT", "/admin/sla/policies/" + id(7), 200);
        verify(auditoria).record("ADMIN_UPDATE", "sla/policies", id(7));
    }

    @Test
    void altaYBajaUsanSuAccion() {
        completar("POST", "/admin/templates", 201);
        completar("DELETE", "/admin/teams/" + id(3) + "/members/" + id(9), 204);
        verify(auditoria).record("ADMIN_CREATE", "templates", null);
        verify(auditoria).record("ADMIN_DELETE", "teams/members", id(3));
    }

    @Test
    void lecturasYFallosNoSeAuditan() {
        completar("GET", "/admin/sla/policies", 200);
        completar("PUT", "/admin/sla/policies/" + id(7), 400);
        completar("PUT", "/admin/sla/policies/" + id(7), 403);
        verify(auditoria, never()).record(anyString(), any(), any());
    }

    @Test
    void lasRutasConAuditoriaPropiaNoSeDuplican() {
        completar("PUT", "/admin/microsoft-graph", 200);
        completar("POST", "/admin/workflows/" + id(4) + "/activate", 200);
        verify(auditoria, never()).record(anyString(), any(), any());
        // Pero la prueba de conexión de Graph, que no se auditaba, sí.
        assertFalse(AuditoriaAdministrativaInterceptor.yaAuditada("POST", "/admin/microsoft-graph/test"));
        assertTrue(AuditoriaAdministrativaInterceptor.yaAuditada("DELETE", "/admin/workflows/" + id(4)));
    }

    @Test
    void entidadEIdSeSacanDeLaRuta() {
        assertEquals("branding/logo", AuditoriaAdministrativaInterceptor.entidad("/admin/branding/logo"));
        assertNull(AuditoriaAdministrativaInterceptor.primerId("/admin/branding/logo"));
        assertEquals(id(12), AuditoriaAdministrativaInterceptor.primerId("/admin/evaluations/" + id(12) + "/review"));
    }
}
