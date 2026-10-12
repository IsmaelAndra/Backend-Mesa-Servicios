package com.mesaayuda.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * La IP de los limites no puede ser un dato que elige el que llama.
 *
 * <p>Este es el punto que mas facil se rompe en silencio: los limites por origen
 * de {@code login} y {@code forgot-password} cuentan por IP, y si esa IP sale de
 * una cabecera que acepta cualquiera, el limite no limita nada. Se puede
 * comprobar mandando la misma peticion con dos cabeceras distintas y viendo si
 * las dos cuentan como origenes diferentes. Antes de estos filtros, contaban.
 */
class ClientIpFilterSeguridadTest {

    private static final String IP_PROXY = "10.0.0.5";

    private String ipResuelta(boolean confiar, MockHttpServletRequest peticion) throws Exception {
        ClientIpFilter filtro = new ClientIpFilter(confiar);
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        filtro.doFilter(peticion, respuesta, (req, res) -> { });
        return (String) peticion.getAttribute(ClientIpFilter.ATRIBUTO_IP);
    }

    @Test
    @DisplayName("sin proxy declarado, X-Forwarded-For se ignora")
    void sinProxyLaCabeceraSeIgnora() throws Exception {
        MockHttpServletRequest p = peticion(IP_PROXY);
        p.addHeader("X-Forwarded-For", "203.0.113.9");

        assertEquals(IP_PROXY, ipResuelta(false, p),
                "sin proxy declarado la IP debe ser la conexion directa");
    }

    @Test
    @DisplayName("dos cabeceras distintas NO producen dos origenes distintos")
    void dosCabecerasNoCuentanComoDosOrigenes() throws Exception {
        MockHttpServletRequest a = peticion(IP_PROXY);
        a.addHeader("X-Forwarded-For", "1.1.1.1");
        MockHttpServletRequest b = peticion(IP_PROXY);
        b.addHeader("X-Forwarded-For", "2.2.2.2");
        MockHttpServletRequest c = peticion(IP_PROXY);
        c.addHeader("X-Forwarded-For", "3.3.3.3");

        String ipA = ipResuelta(false, a);
        String ipB = ipResuelta(false, b);
        String ipC = ipResuelta(false, c);

        // Este es el fallo que se cierra: antes estas tres cadenas distintas
        // contaban como tres ataques distintos y el limite nunca se disparaba.
        assertEquals(ipA, ipB);
        assertEquals(ipB, ipC);
    }

    @Test
    @DisplayName("tampoco basta con X-Real-IP ni con Forwarded")
    void lasVariantesTambienSeIgnoran() throws Exception {
        MockHttpServletRequest p = peticion(IP_PROXY);
        p.addHeader("X-Real-IP", "198.51.100.7");
        assertEquals(IP_PROXY, ipResuelta(false, p));

        MockHttpServletRequest q = peticion(IP_PROXY);
        q.addHeader("Forwarded", "for=198.51.100.8");
        assertEquals(IP_PROXY, ipResuelta(false, q));
    }

    @Test
    @DisplayName("con proxy declarado, la cabecera llega a getRemoteAddr y se respeta")
    void conProxyLaCabeceraSiCuenta() throws Exception {
        // Con trust-forwarded-headers=true hay un ForwardedHeaderFilter delante
        // (lo registra WebConfig) que ya reescribio la peticion. Aqui se
        // comprueba que cuando la peticion llega ya reescrita, el filtro respeta
        // esa IP en vez de inventarse otra.
        MockHttpServletRequest p = peticion("203.0.113.9");
        p.addHeader("X-Forwarded-For", "203.0.113.9");

        assertEquals("203.0.113.9", ipResuelta(true, p));
    }

    @Test
    @DisplayName("sin cabecera y sin remoto, no devuelve null ni cadena vacia")
    void origenVacioNoRompeLaClaveDelLimite() throws Exception {
        MockHttpServletRequest p = peticion(null);

        String ip = ipResuelta(false, p);

        assertEquals("desconocido", ip);
    }

    @Test
    @DisplayName("el atributo queda siempre puesto, para que ningun consumidor adivine")
    void elAtributoSiempreEsta() throws Exception {
        MockHttpServletRequest p = peticion(IP_PROXY);
        MockHttpServletResponse r = new MockHttpServletResponse();
        boolean[] llamado = {false};

        new ClientIpFilter(false).doFilter(p, r, (req, res) -> llamado[0] = true);

        assertEquals(true, llamado[0], "el filtro debe dejar pasar la peticion");
        assertEquals(IP_PROXY, p.getAttribute(ClientIpFilter.ATRIBUTO_IP));
        assertNotEquals(null, p.getAttribute(ClientIpFilter.ATRIBUTO_IP));
    }

    private MockHttpServletRequest peticion(String remoto) {
        MockHttpServletRequest p = new MockHttpServletRequest("POST", "/api/auth/login");
        p.setRemoteAddr(remoto);
        return p;
    }
}