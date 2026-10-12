package com.mesaayuda.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mapa de estado en memoria con caducidad y tope.
 *
 * <p>Sustituye a un ConcurrentHashMap sin límites en el estado de chat del
 * asistente IA. Lo que se comprueba aquí son los dos riesgos que tenía aquel:
 * que creciera sin control y que guardara entradas para siempre.
 */
class ExpiringStateMapTest {

    /** Reloj que se mueve a voluntad, para no esperar de verdad. */
    private Instant ahora = Instant.parse("2026-01-01T10:00:00Z");

    private final Clock reloj = new Clock() {
        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    };

    private ExpiringStateMap<String, String> mapa(Duration ttl, int max) {
        return new ExpiringStateMap<>(ttl, max, reloj);
    }

    private void pasar(Duration d) {
        ahora = ahora.plus(d);
    }

    @Test
    @DisplayName("guarda y devuelve lo que se le puso")
    void guardaYDevuelve() {
        ExpiringStateMap<String, String> m = mapa(Duration.ofMinutes(5), 10);

        m.put("a", "uno");

        assertEquals("uno", m.get("a"));
        assertEquals(1, m.size());
    }

    @Test
    @DisplayName("una clave que no existe devuelve null, no revienta")
    void claveAusente() {
        ExpiringStateMap<String, String> m = mapa(Duration.ofMinutes(5), 10);

        assertNull(m.get("no-existe"));
    }

    @Test
    @DisplayName("sobrescribir renueva el valor")
    void sobrescribir() {
        ExpiringStateMap<String, String> m = mapa(Duration.ofMinutes(5), 10);

        m.put("a", "uno");
        m.put("a", "dos");

        assertEquals("dos", m.get("a"));
        assertEquals(1, m.size());
    }

    @Test
    @DisplayName("guardar null borra la entrada en vez de guardar un hueco")
    void nullBorra() {
        // Un interaction_id nulo significa "no hay conversación que encadenar",
        // y dejar una entrada con valor nulo obligaría a comprobarlo en cada uso.
        ExpiringStateMap<String, String> m = mapa(Duration.ofMinutes(5), 10);
        m.put("a", "uno");

        m.put("a", null);

        assertNull(m.get("a"));
        assertEquals(0, m.size());
    }

    @Test
    @DisplayName("remove borra la entrada")
    void remove() {
        ExpiringStateMap<String, String> m = mapa(Duration.ofMinutes(5), 10);
        m.put("a", "uno");

        m.remove("a");

        assertNull(m.get("a"));
        assertEquals(0, m.size());
    }

    @Test
    @DisplayName("una entrada caducada se lee como si no existiera")
    void loCaducadoDesaparece() {
        ExpiringStateMap<String, String> m = mapa(Duration.ofMinutes(30), 10);
        m.put("a", "uno");

        pasar(Duration.ofMinutes(31));

        assertNull(m.get("a"));
    }

    @Test
    @DisplayName("una entrada que aún no venció se lee bien")
    void loNoCaducadoSeLee() {
        ExpiringStateMap<String, String> m = mapa(Duration.ofMinutes(30), 10);
        m.put("a", "uno");

        pasar(Duration.ofMinutes(29));

        assertEquals("uno", m.get("a"));
    }

    @Test
    @DisplayName("escribir de nuevo renueva la vigencia")
    void escribirRenueva() {
        ExpiringStateMap<String, String> m = mapa(Duration.ofMinutes(30), 10);
        m.put("a", "uno");

        pasar(Duration.ofMinutes(20));
        m.put("a", "otro");
        pasar(Duration.ofMinutes(20));

        // Si escribir no renewara, a los 40 minutos la entrada habria caducado.
        assertEquals("otro", m.get("a"));
    }

    @Test
    @DisplayName("leer una entrada caducada la libera de memoria")
    void leerCaducadoLibera() {
        ExpiringStateMap<String, String> m = mapa(Duration.ofMinutes(30), 10);
        m.put("a", "uno");
        pasar(Duration.ofMinutes(31));

        m.get("a");

        // Si el borrado no ocurriera al leer, una sesión abandonada ocuparía su
        // hueco hasta que alguien escribiera otra vez con esa misma clave.
        assertEquals(0, m.size());
    }

    @Test
    @DisplayName("el tamaño respeta el tope: no crece sin control")
    void respetaElTope() {
        // El fallo original: la clave la elige el cliente, así que un bucle de
        // llamadas con claves distintas hacía crecer el mapa sin límite.
        ExpiringStateMap<String, String> m = mapa(Duration.ofMinutes(30), 100);

        for (int i = 0; i < 5_000; i++) {
            m.put("sesion-" + i, "interaction-" + i);
        }

        assertEquals(100, m.size());
    }

    @Test
    @DisplayName("al superar el tope sale primero la entrada más antigua")
    void saleLaMasAntigua() {
        ExpiringStateMap<String, String> m = mapa(Duration.ofMinutes(30), 3);
        m.put("a", "1");
        m.put("b", "2");
        m.put("c", "3");

        m.put("d", "4");

        // El orden es de inserción, no de acceso: "a" se descarta aunque se acaba
        // de usar, porque una sesión muy activa no debe renovarse eternamente.
        assertNull(m.get("a"));
        assertEquals("2", m.get("b"));
        assertEquals("4", m.get("d"));
        assertEquals(3, m.size());
    }

    @Test
    @DisplayName("no tira entradas vigentes para hacer hueco")
    void noDescartaEntradasVigentes() {
        ExpiringStateMap<String, String> m = mapa(Duration.ofMinutes(30), 2);
        m.put("a", "1");
        m.put("b", "2");

        m.put("c", "3");

        assertNotNull(m.get("b"));
        assertEquals("2", m.get("b"));
    }

    @Test
    @DisplayName("las entradas caducadas no cuentan para el tope")
    void caducadasNoCuentan() {
        ExpiringStateMap<String, String> m = mapa(Duration.ofMinutes(30), 10);
        for (int i = 0; i < 5; i++) {
            m.put("vieja-" + i, "x");
        }

        // Se caducan todas y se sigue escribiendo: el mapa no debe llenarse de
        // basura que ya no le sirve a nadie.
        pasar(Duration.ofMinutes(31));
        m.put("nueva", "x");

        assertEquals(1, m.size());
    }

    @Test
    @DisplayName("clear vacía el mapa")
    void clear() {
        ExpiringStateMap<String, String> m = mapa(Duration.ofMinutes(5), 10);
        m.put("a", "1");
        m.put("b", "2");

        m.clear();

        assertEquals(0, m.size());
        assertNull(m.get("a"));
    }

    @Test
    @DisplayName("aguanta el uso concurrente sin perder ni corromper entradas")
    void usoConcurrente() {
        // Se accede desde varios hilos de petición a la vez. Con un mapa no
        // sincronizado esto daría corrupción estructural; aquí la garantía es que
        // no lance y que el recuento cuadre.
        int hilos = 8;
        int porHilo = 200;
        ExpiringStateMap<String, String> m = mapa(Duration.ofMinutes(30), hilos * porHilo);

        List<Thread> tareas = new ArrayList<>();
        for (int h = 0; h < hilos; h++) {
            final int id = h;
            Thread t = new Thread(() -> {
                for (int i = 0; i < porHilo; i++) {
                    String clave = "s" + id + "-" + i;
                    m.put(clave, "v");
                    m.get(clave);
                }
            });
            tareas.add(t);
            t.start();
        }
        for (Thread t : tareas) {
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        assertEquals(hilos * porHilo, m.size());
        assertTrue(m.size() <= hilos * porHilo);
    }
}