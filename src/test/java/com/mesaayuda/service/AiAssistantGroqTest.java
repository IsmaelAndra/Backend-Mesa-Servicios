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
}
