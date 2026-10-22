package com.mesaayuda.workflow;

import static com.mesaayuda.IdsPrueba.id;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Variables del workflow: espacios de nombre, sustitución {{...}} y configuración con variables. */
class WorkflowVariablesTest {

    private WorkflowContext contexto() {
        WorkflowContext c = new WorkflowContext(id(1), id(2), id(3), "k", 0);
        c.cargarVariables(Map.of("ticketId", 77, "evento", Map.of("prioridad", "BAJA")));
        // Lo vivo viene del resolvedor; aquí se simula para no tocar la base.
        c.setResolvedor(ruta -> switch (ruta) {
            case "ticket.titulo" -> "Impresora <b>caída</b>";
            case "ticket.prioridad" -> "CRITICA";
            default -> null;
        });
        return c;
    }

    @Test
    void ticketSeLeeVivoYEventoEsLaFoto() {
        WorkflowContext c = contexto();
        // La foto del evento decía BAJA; el ticket ahora es CRITICA. Tras una
        // espera, una condición sobre ticket.* tiene que ver lo actual.
        assertEquals("CRITICA", c.leer("ticket.prioridad"));
        assertEquals("BAJA", c.leer("evento.prioridad"));
    }

    @Test
    void interpolaVariablesYDejaVaciasLasQueNoExisten() {
        WorkflowContext c = contexto();
        c.escribir("nodos.mapi1.resumen", "Falla de hardware");
        assertEquals("Ticket #77 (CRITICA): Falla de hardware. []",
                c.interpolar("Ticket #{{ticketId}} ({{ ticket.prioridad }}): {{nodos.mapi1.resumen}}. [{{no.existe}}]"));
    }

    @Test
    void enCorreoLosValoresSeEscapanParaHtml() {
        // El título lo escribe el solicitante: no puede meter marcado en un correo institucional.
        assertEquals("<p>Impresora &lt;b&gt;caída&lt;/b&gt;</p>",
                contexto().interpolarHtml("<p>{{ticket.titulo}}</p>"));
    }

    @Test
    void laConfiguracionAdmiteVariablesEnLosIds() {
        WorkflowContext c = contexto();
        c.escribir("nodos.mapi1.equipoId", id(4));
        WorkflowNodeDef nodo = new WorkflowNodeDef("asignar", "TICKET_ASSIGN_TEAM", "Asignar",
                Map.of("equipoId", "{{nodos.mapi1.equipoId}}", "vacio", "  "), 0.0, 0.0);

        assertEquals(id(4), c.configUuid(nodo, "equipoId"));
        assertNull(c.configTexto(nodo, "vacio"));
        assertNull(c.configUuid(nodo, "inexistente"));
    }

    @Test
    void elCatalogoDeVariablesTieneLasDelTicketYLasDelUsuario() {
        List<String> claves = WorkflowVariables.clavesVivas();
        assertEquals(true, claves.contains("ticket.prioridad"));
        assertEquals(true, claves.contains("ticket.sla.venceResolucion"));
        assertEquals(true, claves.contains("usuario.rol"));
    }
}
