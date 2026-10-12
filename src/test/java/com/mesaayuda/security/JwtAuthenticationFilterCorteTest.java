package com.mesaayuda.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Corte de tokens de acceso tras logout o cambio de contraseña, y cuentas que
 * desaparecen con un token todavía vigente.
 */
class JwtAuthenticationFilterCorteTest {

    private static final String TOKEN = "jwt-de-prueba";
    private static final String CORREO = "agente@coac.test";

    private JwtTokenProvider tokenProvider;
    private CustomUserDetailsService userDetailsService;
    private JwtAuthenticationFilter filtro;
    private FilterChain cadena;
    private MockHttpServletRequest peticion;
    private MockHttpServletResponse respuesta;

    @BeforeEach
    void setUp() {
        tokenProvider = mock(JwtTokenProvider.class);
        userDetailsService = mock(CustomUserDetailsService.class);
        filtro = new JwtAuthenticationFilter();
        ReflectionTestUtils.setField(filtro, "tokenProvider", tokenProvider);
        ReflectionTestUtils.setField(filtro, "userDetailsService", userDetailsService);
        cadena = mock(FilterChain.class);
        peticion = new MockHttpServletRequest("GET", "/tickets");
        peticion.setServletPath("/tickets");
        peticion.addHeader("Authorization", "Bearer " + TOKEN);
        respuesta = new MockHttpServletResponse();
        when(tokenProvider.validateToken(TOKEN)).thenReturn(true);
        when(tokenProvider.getUsernameFromToken(TOKEN)).thenReturn(CORREO);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private void usuarioConCorte(Instant corte) {
        when(userDetailsService.loadUserByUsername(CORREO)).thenReturn(new MesaUserDetails(
                CORREO, "hash", List.of(new SimpleGrantedAuthority("ROLE_AGENT")), false, corte));
    }

    @Test
    void tokenEmitidoAntesDelLogoutNoAutentica() throws Exception {
        Instant logout = Instant.parse("2026-10-06T15:00:00Z");
        usuarioConCorte(logout);
        when(tokenProvider.getIssuedAtFromToken(TOKEN)).thenReturn(Date.from(logout.minusSeconds(60)));

        filtro.doFilter(peticion, respuesta, cadena);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(cadena).doFilter(peticion, respuesta);
    }

    @Test
    void tokenEmitidoDespuesDelCorteSiAutentica() throws Exception {
        Instant corte = Instant.parse("2026-10-06T15:00:00.700Z");
        usuarioConCorte(corte);
        // Mismo segundo que el corte: es el token que se emite justo después de
        // cambiar la contraseña, y el iat del JWT no lleva milisegundos.
        when(tokenProvider.getIssuedAtFromToken(TOKEN)).thenReturn(Date.from(Instant.parse("2026-10-06T15:00:00Z")));

        filtro.doFilter(peticion, respuesta, cadena);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void sinCorteSeAutenticaComoSiempre() throws Exception {
        usuarioConCorte(null);

        filtro.doFilter(peticion, respuesta, cadena);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void cuentaDesactivadaConTokenVigenteNoDa500() throws Exception {
        when(userDetailsService.loadUserByUsername(CORREO))
                .thenThrow(new UsernameNotFoundException("Usuario inactivo"));

        // Antes la excepción salía del filtro y el cliente recibía un 500.
        filtro.doFilter(peticion, respuesta, cadena);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(cadena).doFilter(peticion, respuesta);
        assertEquals(200, respuesta.getStatus());
    }
}
