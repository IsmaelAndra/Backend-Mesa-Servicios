package com.mesaayuda.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Parámetros que se envían a GroqCloud (los que recomienda para la capa gratuita). */
class AiAssistantGroqTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void gptOssUsaTemperaturaBajaTopeYRazonamientoLow() throws Exception {
        JsonNode o = mapper.readTree("{" + AiAssistantService.opcionesGroq("openai/gpt-oss-20b") + "}");
        assertEquals(0.2, o.path("temperature").asDouble());
        assertEquals(800, o.path("max_completion_tokens").asInt());
        assertEquals("low", o.path("reasoning_effort").asText());
    }

    @Test
    void otrosModelosNoLlevanReasoningEffort() throws Exception {
        JsonNode o = mapper.readTree("{" + AiAssistantService.opcionesGroq("llama-3.1-8b-instant") + "}");
        assertTrue(o.path("reasoning_effort").isMissingNode());
        assertEquals(0.2, o.path("temperature").asDouble());
    }

    @Test
    void elChatHablaDistintoAUnSolicitanteQueAlPersonal() {
        String usuario = AiAssistantService.instruccionesPorRol("USER");
        assertTrue(usuario.contains("usuario final"));
        assertTrue(usuario.contains("No le des"));
        assertTrue(AiAssistantService.instruccionesPorRol("AREA_MANAGER").contains("usuario final"));
        assertTrue(AiAssistantService.instruccionesPorRol(null).contains("usuario final"));
        assertTrue(AiAssistantService.instruccionesPorRol("AGENT").contains("personal de TI"));
        assertTrue(AiAssistantService.instruccionesPorRol("SUPER_ADMIN").contains("personal de TI"));
    }

    @Test
    void laCategoriaLlevaSuDescripcionYEquipoParaLaIa() {
        com.mesaayuda.entity.Category c = new com.mesaayuda.entity.Category();
        c.setNameCategory("Correo");
        c.setDescriptionCategory("Problemas con correo corporativo");
        com.mesaayuda.entity.SupportTeam t = new com.mesaayuda.entity.SupportTeam();
        t.setNameTeam("Equipo TI");
        c.setTeamCategory(t);
        assertEquals("- Correo: Problemas con correo corporativo (equipo: Equipo TI)",
                AiAssistantService.describirCategoria(c));
    }
}
