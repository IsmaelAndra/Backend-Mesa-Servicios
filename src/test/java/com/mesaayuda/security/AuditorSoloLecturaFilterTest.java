package com.mesaayuda.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** El auditor consulta todo y no cambia nada, salvo su propia sesión y cuenta. */
class AuditorSoloLecturaFilterTest {

    private final AuditorSoloLecturaFilter filtro = new AuditorSoloLecturaFilter();

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private void como(String rol) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "persona@coac.test", null, List.of(new SimpleGrantedAuthority("ROLE_" + rol))));
    }

    private MockHttpServletResponse pasar(String metodo, String ruta, FilterChain cadena) throws Exception {
        MockHttpServletRequest peticion = new MockHttpServletRequest(metodo, ruta);
        peticion.setServletPath(ruta);
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        filtro.doFilter(peticion, respuesta, cadena);
        return respuesta;
    }

    @Test
    @DisplayName("el auditor puede leer cualquier cosa")
    void elAuditorLee() throws Exception {
        como("AUDITOR");
        FilterChain cadena = mock(FilterChain.class);
        MockHttpServletResponse r = pasar("GET", "/tickets", cadena);
        verify(cadena).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        assertEquals(200, r.getStatus());
    }

    @Test
    @DisplayName("el auditor no puede escribir: 403 sin llegar al controlador")
    void elAuditorNoEscribe() throws Exception {
        como("AUDITOR");
        for (String metodo : List.of("POST", "PUT", "PATCH", "DELETE")) {
            FilterChain cadena = mock(FilterChain.class);
            MockHttpServletResponse r = pasar(metodo, "/tickets/5/status", cadena);
            verify(cadena, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
            assertEquals(403, r.getStatus(), metodo);
        }
    }

    @Test
    @DisplayName("el auditor sí gestiona su sesión, su cuenta y sus avisos")
    void elAuditorGestionaLoSuyo() throws Exception {
        como("AUDITOR");
        for (String ruta : List.of("/auth/logout", "/auth/change-password", "/users/me",
                "/notifications/read-all", "/events/ticket")) {
            FilterChain cadena = mock(FilterChain.class);
            pasar("POST", ruta, cadena);
            verify(cadena).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        }
    }

    @Test
    @DisplayName("un prefijo suelto no cuela: /authx no es /auth")
    void unPrefijoSueltoNoCuela() {
        assertTrue(AuditorSoloLecturaFilter.permitida("/auth"));
        assertTrue(AuditorSoloLecturaFilter.permitida("/auth/login"));
        assertFalse(AuditorSoloLecturaFilter.permitida("/authx"));
        assertFalse(AuditorSoloLecturaFilter.permitida("/users/5"));
        assertFalse(AuditorSoloLecturaFilter.permitida(null));
        // Pedir acceso a un confidencial sí, y solo esa ruta.
        assertTrue(AuditorSoloLecturaFilter.permitida("/tickets/5/acceso-confidencial"));
        assertFalse(AuditorSoloLecturaFilter.permitida("/tickets/5/acceso-confidencial/otra"));
        assertFalse(AuditorSoloLecturaFilter.permitida("/tickets/accesos-confidenciales/x/decidir"));
    }

    @Test
    @DisplayName("al resto de roles el filtro no les afecta")
    void losDemasRolesNoSeTocan() throws Exception {
        como("AGENT");
        FilterChain cadena = mock(FilterChain.class);
        MockHttpServletResponse r = pasar("POST", "/tickets/5/status", cadena);
        verify(cadena).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        assertEquals(200, r.getStatus());
    }
}
