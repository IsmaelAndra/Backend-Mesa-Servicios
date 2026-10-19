package com.mesaayuda.service;

import java.util.UUID;
import static com.mesaayuda.IdsPrueba.id;
import com.mesaayuda.entity.Category;
import com.mesaayuda.entity.Priority;
import com.mesaayuda.entity.SupportTeam;
import com.mesaayuda.exception.AiException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * CLASIFICAR de NEXA: la respuesta de la IA se contrasta con los catálogos reales.
 * Un workflow decide con esto ("si NEXA dice CRÍTICA, escalar"), así que un valor
 * inventado no puede pasar.
 */
class NexaClasificacionTest {

    private final AiAssistantService servicio = new AiAssistantService();

    private final List<Priority> prioridades = List.of(prioridad(id(1), "BAJA"), prioridad(id(4), "CRITICA"));
    private final List<Category> categorias = List.of(categoria(id(2), "Soporte Técnico"));
    private final List<SupportTeam> equipos = List.of(equipo(id(3), "Infraestructura"));

    @Test
    void reconoceLosValoresAunqueVenganConTildesYMinusculas() {
        Map<String, Object> r = servicio.interpretarClasificacion(
                "Aquí está:\n```json\n{\"prioridad\":\"Crítica\",\"categoria\":\"soporte tecnico\","
                        + "\"equipo\":\"INFRAESTRUCTURA\",\"resumen\":\"Servidor caído\",\"confianza\":0.9}\n```",
                prioridades, categorias, equipos);

        assertEquals("CRITICA", r.get("prioridad"));
        assertEquals(id(4), r.get("prioridadId"));
        assertEquals("Soporte Técnico", r.get("categoria"));
        assertEquals(id(3), r.get("equipoId"));
        assertEquals("Servidor caído", r.get("resumen"));
        assertEquals(0.9, r.get("confianza"));
    }

    @Test
    void tambienReconoceElTipoDeTicket() {
        com.mesaayuda.entity.TicketType req = new com.mesaayuda.entity.TicketType();
        req.setIdTicketType(id(7));
        req.setNameTicketType("REQUERIMIENTO");
        Map<String, Object> r = servicio.interpretarClasificacion(
                "{\"prioridad\":\"BAJA\",\"tipo\":\"requerimiento\",\"confianza\":0.8}",
                prioridades, categorias, equipos, List.of(req));

        assertEquals("REQUERIMIENTO", r.get("tipo"));
        assertEquals(id(7), r.get("tipoId"));
    }

    @Test
    void unValorQueNoEstaEnElCatalogoSeDescarta() {
        Map<String, Object> r = servicio.interpretarClasificacion(
                "{\"prioridad\":\"URGENTISIMA\",\"categoria\":null,\"equipo\":\"Marketing\",\"confianza\":7}",
                prioridades, categorias, equipos);

        assertNull(r.get("prioridad"));
        assertNull(r.get("prioridadId"));
        assertNull(r.get("categoria"));
        assertNull(r.get("equipoId"));
        assertEquals(1.0, r.get("confianza"), "la confianza se acota a [0, 1]");
    }

    @Test
    void unaRespuestaQueNoEsJsonFallaConMotivo() {
        assertThrows(AiException.class, () -> servicio.interpretarClasificacion(
                "Creo que es urgente.", prioridades, categorias, equipos));
    }

    private static Priority prioridad(UUID id, String nombre) {
        Priority p = new Priority();
        p.setIdPriority(id);
        p.setNamePriority(nombre);
        return p;
    }

    private static Category categoria(UUID id, String nombre) {
        Category c = new Category();
        c.setIdCategory(id);
        c.setNameCategory(nombre);
        return c;
    }

    private static SupportTeam equipo(UUID id, String nombre) {
        SupportTeam t = new SupportTeam();
        t.setIdTeam(id);
        t.setNameTeam(nombre);
        return t;
    }
}
