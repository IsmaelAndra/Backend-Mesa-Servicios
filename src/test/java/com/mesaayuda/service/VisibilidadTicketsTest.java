package com.mesaayuda.service;

import com.mesaayuda.entity.Area;
import com.mesaayuda.entity.Role;
import com.mesaayuda.entity.Ticket;
import com.mesaayuda.entity.User;
import com.mesaayuda.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static com.mesaayuda.IdsPrueba.id;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Quién ve qué ticket en la mesa multi-área. Tecnología = área 1, Contabilidad
 * = área 2, Desarrollo = área 3.
 */
class VisibilidadTicketsTest {

    private static final UUID TECNOLOGIA = id(1);
    private static final UUID CONTABILIDAD = id(2);
    private static final UUID DESARROLLO = id(3);

    private TicketAccessPolicy politica;


    @BeforeEach
    void setUp() {

        politica = new TicketAccessPolicy();
        ReflectionTestUtils.setField(politica, "jefaturaService",
                new JefaturaService(mock(UserRepository.class)));
    }

    private static Area area(UUID id) {
        Area a = new Area();
        a.setIdArea(id);
        return a;
    }

    private static User persona(int n, String rol, UUID area) {
        User u = new User();
        u.setIdUser(id(100 + n));
        Role r = new Role();
        r.setNameRole(rol);
        u.setRolUser(r);
        u.setArea(area(area));
        return u;
    }

    /** Ticket pedido por alguien de areaSolicitante que atiende areaQueAtiende. */
    private static Ticket ticket(User solicitante, UUID areaSolicitante, UUID areaQueAtiende, User agente) {
        Ticket t = new Ticket();
        t.setUserTicket(solicitante);
        t.setAreaTicket(area(areaSolicitante));
        t.setAreaDestinoTicket(area(areaQueAtiende));
        t.setAgentTicket(agente);
        return t;
    }

    @Test
    @DisplayName("el súper admin y el auditor ven todo")
    void superAdminYAuditorVenTodo() {
        Ticket t = ticket(persona(1, "USER", CONTABILIDAD), CONTABILIDAD, DESARROLLO, null);
        assertTrue(politica.puedeLeer(t, persona(2, "SUPER_ADMIN", TECNOLOGIA)));
        assertTrue(politica.puedeLeer(t, persona(3, "AUDITOR", TECNOLOGIA)));
    }

    @Test
    @DisplayName("el agente ve los de su área, no los de otra")
    void elAgenteVeSoloSuArea() {
        User agenteTec = persona(4, "AGENT", TECNOLOGIA);
        User solicitante = persona(1, "USER", CONTABILIDAD);
        assertTrue(politica.puedeLeer(ticket(solicitante, CONTABILIDAD, TECNOLOGIA, null), agenteTec));
        assertFalse(politica.puedeLeer(ticket(solicitante, CONTABILIDAD, DESARROLLO, null), agenteTec));
    }

    @Test
    @DisplayName("el agente sigue viendo lo que pidió o tiene asignado, aunque sea de otra área")
    void elAgenteVeLoSuyo() {
        User agenteTec = persona(4, "AGENT", TECNOLOGIA);
        assertTrue(politica.puedeLeer(ticket(agenteTec, TECNOLOGIA, DESARROLLO, null), agenteTec));
        assertTrue(politica.puedeLeer(ticket(persona(1, "USER", CONTABILIDAD), CONTABILIDAD, DESARROLLO, agenteTec), agenteTec));
    }

    @Test
    @DisplayName("el jefe ve lo que atiende su área y, en seguimiento, lo que pide su gente")
    void elJefeVeSuAreaYSuGente() {
        User jefeConta = persona(5, "AREA_MANAGER", CONTABILIDAD);
        User deConta = persona(1, "USER", CONTABILIDAD);
        User deTec = persona(6, "USER", TECNOLOGIA);

        // Pedido por su gente a Tecnología: lo ve (seguimiento)...
        Ticket seguimiento = ticket(deConta, CONTABILIDAD, TECNOLOGIA, null);
        assertTrue(politica.puedeLeer(seguimiento, jefeConta));
        // ...comenta, pero no lo gestiona.
        assertTrue(politica.puedeComentar(seguimiento, jefeConta));
        assertFalse(politica.puedeEscribir(seguimiento, jefeConta));

        // Lo que atiende su área: lo ve y lo gestiona.
        Ticket propio = ticket(deTec, TECNOLOGIA, CONTABILIDAD, null);
        assertTrue(politica.puedeLeer(propio, jefeConta));
        assertTrue(politica.puedeEscribir(propio, jefeConta));

        // Ni su gente ni su área: fuera.
        Ticket ajeno = ticket(deTec, TECNOLOGIA, DESARROLLO, null);
        assertFalse(politica.puedeLeer(ajeno, jefeConta));
        assertFalse(politica.puedeComentar(ajeno, jefeConta));
    }

    @Test
    @DisplayName("el usuario solo ve los suyos")
    void elUsuarioSoloVeLosSuyos() {
        User u = persona(1, "USER", CONTABILIDAD);
        assertTrue(politica.puedeLeer(ticket(u, CONTABILIDAD, TECNOLOGIA, null), u));
        assertFalse(politica.puedeLeer(ticket(persona(7, "USER", CONTABILIDAD), CONTABILIDAD, CONTABILIDAD, null), u));
    }

    @Test
    @DisplayName("los eventos en vivo siguen la misma regla")
    void losEventosEnVivoSiguenLaRegla() {
        AlcanceTickets agenteTec = politica.alcanceDe(persona(4, "AGENT", TECNOLOGIA));
        assertTrue(agenteTec.incluye(id(1), null, TECNOLOGIA, CONTABILIDAD));
        assertFalse(agenteTec.incluye(id(1), null, DESARROLLO, CONTABILIDAD));
        assertTrue(AlcanceTickets.todo(id(9)).incluye(null, null, DESARROLLO, null));
        assertFalse(AlcanceTickets.soloPropios(id(9)).incluye(id(1), null, DESARROLLO, null));
        assertTrue(AlcanceTickets.soloPropios(id(9)).incluye(id(9), null, DESARROLLO, null));
    }

    // ============================ CONFIDENCIALES ============================

    private static Ticket confidencial(User solicitante, UUID areaSolicitante, UUID areaQueAtiende, User agente) {
        Ticket t = ticket(solicitante, areaSolicitante, areaQueAtiende, agente);
        t.setIdTicket(id(500));
        t.setConfidencialTicket(true);
        return t;
    }

    @Test
    @DisplayName("un confidencial lo ven el solicitante, el agente asignado y el jefe del área que lo atiende")
    void confidencialLoVenLosDirectos() {
        User solicitante = persona(1, "USER", CONTABILIDAD);
        User agente = persona(4, "AGENT", TECNOLOGIA);
        User jefeTec = persona(8, "AREA_MANAGER", TECNOLOGIA);
        Ticket t = confidencial(solicitante, CONTABILIDAD, TECNOLOGIA, agente);

        assertTrue(politica.puedeLeer(t, solicitante));
        assertTrue(politica.puedeLeer(t, agente));
        assertTrue(politica.puedeLeer(t, jefeTec));
        assertTrue(politica.puedeEscribir(t, jefeTec));
    }

    @Test
    @DisplayName("un confidencial no lo ven el súper admin, el auditor, el seguimiento del jefe ni otros agentes del área")
    void confidencialNoLoVenLosDemas() {
        User solicitante = persona(1, "USER", CONTABILIDAD);
        User jefeConta = persona(5, "AREA_MANAGER", CONTABILIDAD);
        Ticket t = confidencial(solicitante, CONTABILIDAD, TECNOLOGIA, null);

        assertFalse(politica.puedeLeer(t, persona(2, "SUPER_ADMIN", TECNOLOGIA)));
        assertFalse(politica.puedeLeer(t, persona(3, "AUDITOR", TECNOLOGIA)));
        assertFalse(politica.puedeLeer(t, jefeConta));
        assertFalse(politica.puedeComentar(t, jefeConta));
        assertFalse(politica.puedeLeer(t, persona(9, "AGENT", TECNOLOGIA)));
        assertFalse(politica.puedeEscribir(t, persona(2, "SUPER_ADMIN", TECNOLOGIA)));
    }

    @Test
    @DisplayName("abierto a toda el área, lo ven los agentes de esa área")
    void confidencialAbiertoAlArea() {
        Ticket t = confidencial(persona(1, "USER", CONTABILIDAD), CONTABILIDAD, TECNOLOGIA, null);
        t.setConfidencialAreaTicket(true);
        assertTrue(politica.puedeLeer(t, persona(9, "AGENT", TECNOLOGIA)));
        assertFalse(politica.puedeLeer(t, persona(10, "AGENT", DESARROLLO)));
    }

    @Test
    @DisplayName("con autorización (persona añadida o acceso aprobado) se puede leer")
    void confidencialConAutorizacion() {
        AlcanceTickets conPermiso = new AlcanceTickets(true, id(2), java.util.Set.of(), java.util.Set.of(),
                java.util.Set.of(), java.util.Set.of(id(500)));
        assertTrue(conPermiso.veConfidencial(id(500), id(1), null, TECNOLOGIA, false));
        assertFalse(conPermiso.veConfidencial(id(501), id(1), null, TECNOLOGIA, false));
    }

    @Test
    @DisplayName("solo al súper admin se le dice que es confidencial; a otros, un 403 normal")
    void elAvisoDeConfidencialEsSoloParaQuienPuedePedirAcceso() {
        Ticket t = confidencial(persona(1, "USER", CONTABILIDAD), CONTABILIDAD, TECNOLOGIA, null);
        UserService us = mock(UserService.class);
        ReflectionTestUtils.setField(politica, "userService", us);

        when(us.getCurrentUser()).thenReturn(persona(2, "SUPER_ADMIN", TECNOLOGIA));
        org.junit.jupiter.api.Assertions.assertThrows(com.mesaayuda.exception.TicketConfidencialException.class,
                () -> politica.exigirLectura(t, "leer", id(500)));

        when(us.getCurrentUser()).thenReturn(persona(9, "AGENT", DESARROLLO));
        org.springframework.security.access.AccessDeniedException e = org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> politica.exigirLectura(t, "leer", id(500)));
        assertFalse(e instanceof com.mesaayuda.exception.TicketConfidencialException);
    }
}
