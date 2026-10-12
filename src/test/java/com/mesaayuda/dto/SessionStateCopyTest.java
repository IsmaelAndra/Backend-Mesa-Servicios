package com.mesaayuda.dto;

import java.util.UUID;
import static com.mesaayuda.IdsPrueba.id;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Estos tests fijan un bug real: el método de copia pasaba el campo
 * userSessionState en lugar del parámetro sessionState, así que el estado
 * nunca se aplicaba y la respuesta salía siempre nula. Pasaba inadvertido
 * porque el resto de la copia sí era correcto.
 */
class SessionStateCopyTest {

    private TicketResponse ticket(UUID id, UUID userId, String sessionState) {
return new TicketResponse(id, userId, "titulo", "descripcion", "correo", "Nombre",
                  null, "ACTIVE", sessionState, "agente", "equipo", "area", null, null, "categoria",
                  "tipo", "ABIERTO", "ALTA", 1, null, null, null, null);
    }

private UserResponse user(UUID id, String statusUser, String sessionState) {
          return new UserResponse(id, "0102030405", "correo@coac.gob.ec", "0999999999",
                  statusUser, sessionState, "AGENT", "area", null, null, "Nombre", "Nombre", "Apellido",
                  null, null, null, null, null, null);
      }

    @Test
    void ticket_aplicaElEstadoRecibido() {
        TicketResponse r = ticket(id(1), id(77), null).withUserSessionState("EN_LINEA");

        assertEquals("EN_LINEA", r.userSessionState());
    }

    @Test
    void ticket_preservaElRestoDeLosCampos() {
        TicketResponse base = ticket(id(1), id(77), null);
        TicketResponse r = base.withUserSessionState("RECIENTE");

        assertEquals(base.idTicket(), r.idTicket());
        assertEquals(base.userId(), r.userId());
        assertEquals(base.titleTicket(), r.titleTicket());
        assertEquals(base.descriptionTicket(), r.descriptionTicket());
        assertEquals(base.user(), r.user());
        assertEquals(base.userDisplayName(), r.userDisplayName());
        assertEquals(base.userStatus(), r.userStatus());
        assertEquals(base.agent(), r.agent());
        assertEquals(base.status(), r.status());
        assertEquals(base.priorityLevel(), r.priorityLevel());
    }

    @Test
    void ticket_sustituyeElEstadoPrevio() {
        TicketResponse r = ticket(id(1), id(77), "DESCONECTADO").withUserSessionState("EN_LINEA");

        assertEquals("EN_LINEA", r.userSessionState());
    }

    @Test
    void ticket_noCopiaSiElEstadoEsIgual() {
        TicketResponse base = ticket(id(1), id(77), "EN_LINEA");

        assertSame(base, base.withUserSessionState("EN_LINEA"));
    }

    @Test
    void ticket_noCopiaSiElEstadoEsNulo() {
        TicketResponse base = ticket(id(1), id(77), "EN_LINEA");

        assertSame(base, base.withUserSessionState(null));
    }

    @Test
    void usuario_aplicaElEstadoRecibido() {
        UserResponse r = user(id(5), "ACTIVE", null).withSessionState("RECIENTE");

        assertEquals("RECIENTE", r.sessionState());
    }

    @Test
    void usuario_preservaElRestoDeLosCampos() {
        UserResponse base = user(id(5), "INACTIVE", null);
        UserResponse r = base.withSessionState("DESCONECTADO");

        assertEquals(base.idUser(), r.idUser());
        assertEquals(base.identificationUser(), r.identificationUser());
        assertEquals(base.corporateEmailUser(), r.corporateEmailUser());
        assertEquals(base.statusUser(), r.statusUser());
        assertEquals(base.role(), r.role());
        assertEquals(base.area(), r.area());
    }

    @Test
    void usuario_noCopiaSiElEstadoEsIgual() {
        UserResponse base = user(id(5), "ACTIVE", "EN_LINEA");

        assertSame(base, base.withSessionState("EN_LINEA"));
    }
}
