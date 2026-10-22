package com.mesaayuda.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mesaayuda.dto.AnswerResponse;
import com.mesaayuda.dto.AiStatusResponse;
import com.mesaayuda.dto.TicketResponse;
import com.mesaayuda.entity.AiConfig;
import com.mesaayuda.exception.AiException;
import com.mesaayuda.exception.BadRequestException;
import com.mesaayuda.repository.AiConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Asistente IA para la mesa de servicios. Proveedores soportados:
 * <ul>
 *   <li>GEMINI: Google Generative Language API (free tier compatible).</li>
 *   <li>OPENAI-compatible: cualquier API Chat Completions (base_url + key).</li>
 * </ul>
 */
@Service
public class AiAssistantService {

    private static final Logger log = LoggerFactory.getLogger(AiAssistantService.class);

    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    /**
     * Cuánto se conserva el contexto de una conversación con el asistente. Pasado
     * este tiempo, la siguiente pregunta arranca una conversación nueva en vez de
     * encadenar sobre una que Google ya no tiene.
     */
    private static final Duration CHAT_STATE_TTL = Duration.ofMinutes(30);

    /**
     * Tope de conversaciones vivas a la vez. La clave la elige el cliente, así que
     * sin este límite el mapa crecía sin control; al superarlo sale la más antigua,
     * que es la que más probabilidades tiene de estar olvidada.
     */
    private static final int CHAT_STATE_MAX_ENTRIES = 5_000;
    private static final Set<String> ACCIONES = Set.of("ANSWER", "SUMMARY");

    private static final String DEFAULT_GEMINI_BASE = "https://generativelanguage.googleapis.com/v1beta";
    private static final String DEFAULT_OPENAI_BASE = "https://api.openai.com/v1";
    /** GroqCloud: API compatible con OpenAI; la capa gratuita basta para la mesa. */
    private static final String DEFAULT_GROQ_BASE = "https://api.groq.com/openai/v1";
    /** Recomendado por Groq para la capa gratuita: rápido y sigue bien instrucciones. */
    private static final String DEFAULT_GROQ_MODEL = "openai/gpt-oss-20b";
    /**
     * Tope de tokens de respuesta en Groq. Los gpt-oss cuentan también el
     * razonamiento, así que se deja margen sobre los 500 de su ejemplo para que
     * un resumen no salga cortado. Con el cupo de 50 consultas diarias queda muy
     * lejos de los 200.000 tokens al día de la capa gratuita.
     */
    private static final int GROQ_MAX_TOKENS = 800;
    /**
     * Versión vigente del modelo por defecto. Las series 1.x y 2.x fueron deprecadas
     * por Google (responden 404/error) y se normalizan a este modelo.
     */
    private static final String DEFAULT_GEMINI_MODEL = "gemini-3.6-flash";
    /** Revisión de la Interactions API de Gemini requerida por Google. */
    private static final String GEMINI_API_REVISION = "2026-05-20";

    @Autowired
    private AiConfigRepository aiConfigRepository;

    @Autowired
    private TicketService ticketService;

    @Autowired
    private com.mesaayuda.repository.PriorityRepository priorityRepository;

    @Autowired
    private com.mesaayuda.repository.CategoryRepository categoryRepository;

    @Autowired
    private com.mesaayuda.repository.SupportTeamRepository supportTeamRepository;

    @Autowired(required = false)
    private com.mesaayuda.repository.TicketTypeRepository ticketTypeRepository;

    @Autowired
    private com.mesaayuda.security.UrlSafetyValidator urlSafetyValidator;

    /**
     * Descifra la api_key antes de usarla. Se guardaba en claro en la tabla y
     * ahora va cifrada, igual que el secreto de Graph. decryptIfNeeded acepta
     * ambos formatos para no dejar inservible la clave de una instalacion que ya
     * tenga una guardada.
     */
    @Autowired
    private SecretCryptoService secretCryptoService;

    @Autowired(required = false)
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private final ObjectMapper mapper = new ObjectMapper();

/**
     * Conversaciones multi-turn de Gemini: clave = usuario autenticado, valor =
     * último interaction_id.
     *
     * <p>Con caducidad y tope de entradas, no un mapa plano. La clave la elige el
     * cliente, así que sin tope cualquiera con sesión podía mandar un id distinto
     * en cada llamada y hacer crecer el mapa sin límite; y sin caducidad un
     * interaction_id se conservaba para siempre, reenviando contexto de una
     * conversación que Google ya había purgado.
     */
private final ExpiringStateMap<String, String> chatState = new ExpiringStateMap<>(
            CHAT_STATE_TTL, CHAT_STATE_MAX_ENTRIES);

    /**
     * Ejecuta una acción del asistente (sugerencia de respuesta o resumen).
     */
    public String assist(String action, UUID ticketId, String instructions) {
        if (!ACCIONES.contains(action)) {
            throw new BadRequestException("Acción no soportada: " + action);
        }
        AiConfig cfg = enabledConfig();
        exigirAccion(cfg, action);

        String prompt = buildPrompt(action, ticketId, instructions);
        try {
            if ("GEMINI".equalsIgnoreCase(cfg.getProvider())) {
                return callGemini(cfg, prompt, null).text();
            }
            return callOpenAiCompatible(cfg, prompt);
        } catch (AiException e) {
            throw e;
        } catch (Exception e) {
            throw new AiException("No se pudo contactar al proveedor de IA: " + e.getMessage());
        }
    }

    /** Acción de clasificación. Se usa desde el nodo NEXA de los workflows. */
    public static final String ACCION_CLASIFICAR = "CLASSIFY";

    /**
     * Clasifica un ticket: sugiere prioridad, categoría y equipo, y un resumen.
     *
     * <p>Es la misma IA, el mismo proveedor y las mismas reglas de la institución
     * que el resto de acciones; solo cambia lo que se le pide. La respuesta se
     * pide en JSON y se contrasta con los catálogos reales: un valor que no
     * existe en la mesa (una prioridad inventada, un equipo que no está) se
     * descarta en vez de pasarse al workflow, que lo usaría para decidir.
     *
     * @return prioridad, prioridadId, categoria, categoriaId, equipo, equipoId,
     *         resumen y confianza (0 a 1). Los que la IA no pudo determinar van a null.
     */
    public Map<String, Object> clasificar(UUID ticketId, String instructions) {
        AiConfig cfg = enabledConfig();
        exigirAccion(cfg, ACCION_CLASIFICAR);

        List<com.mesaayuda.entity.Priority> prioridades = priorityRepository.findAllActive();
        List<com.mesaayuda.entity.Category> categorias = categoryRepository.findByDeletedCategoryFalse();
        List<com.mesaayuda.entity.SupportTeam> equipos = supportTeamRepository.findByActiveTeamTrue();
        List<com.mesaayuda.entity.TicketType> tipos = ticketTypeRepository == null ? List.of()
                : ticketTypeRepository.findByDeletedTicketTypeFalseOrderByNameTicketTypeAsc();

        TicketResponse ticket = ticketService.findById(ticketId);
        StringBuilder sb = new StringBuilder("Actúa como analista de una mesa de servicios (COAC). ")
                .append("Clasifica el ticket. Responde SOLO con un objeto JSON, sin texto alrededor ni Markdown, ")
                .append("con estas claves: \"prioridad\", \"categoria\", \"tipo\", \"equipo\" (cada una exactamente uno ")
                .append("de los valores permitidos, o null si no puedes decidir), \"resumen\" (máximo 40 palabras ")
                .append("en español) y \"confianza\" (número de 0 a 1).\n\n");
        agregarReglas(sb);
        sb.append("\nPrioridades permitidas: ")
                .append(String.join(", ", prioridades.stream().map(com.mesaayuda.entity.Priority::getNamePriority).toList()))
                .append("\nCategorías permitidas (nombre: para qué sirve). Elige la MÁS ESPECÍFICA que encaje; ")
                .append("usa una genérica solo si ninguna otra corresponde:\n")
                .append(String.join("\n", categorias.stream().map(AiAssistantService::describirCategoria).toList()))
                .append("\nTipos permitidos (nombre: para qué sirve):\n")
                .append(String.join("\n", tipos.stream().map(t -> "- " + t.getNameTicketType()
                        + (t.getDescriptionTicketType() == null || t.getDescriptionTicketType().isBlank()
                                ? "" : ": " + t.getDescriptionTicketType().trim())).toList()))
                .append("\nEquipos permitidos: ")
                .append(String.join(", ", equipos.stream().map(com.mesaayuda.entity.SupportTeam::getNameTeam).toList()))
                .append("\n\nTICKET #").append(ticket.numberTicket())
                .append("\nTítulo: ").append(ticket.titleTicket())
                .append("\nDescripción:\n").append(plain(ticket.descriptionTicket())).append("\n");
        if (instructions != null && !instructions.isBlank()) {
            sb.append("\nCriterios adicionales:\n").append(instructions).append("\n");
        }

        String respuesta;
        try {
            respuesta = "GEMINI".equalsIgnoreCase(cfg.getProvider())
                    ? callGemini(cfg, sb.toString(), null).text()
                    : callOpenAiCompatible(cfg, sb.toString());
        } catch (AiException e) {
            throw e;
        } catch (Exception e) {
            throw new AiException("No se pudo contactar al proveedor de IA: " + e.getMessage());
        }
        return interpretarClasificacion(respuesta, prioridades, categorias, equipos, tipos);
    }

    /** "Correo: Problemas con correo corporativo (equipo: Equipo TI)" para el prompt. */
    static String describirCategoria(com.mesaayuda.entity.Category c) {
        StringBuilder s = new StringBuilder("- ").append(c.getNameCategory());
        if (c.getDescriptionCategory() != null && !c.getDescriptionCategory().isBlank()) {
            s.append(": ").append(c.getDescriptionCategory().trim());
        }
        if (c.getTeamCategory() != null && c.getTeamCategory().getNameTeam() != null) {
            s.append(" (equipo: ").append(c.getTeamCategory().getNameTeam()).append(")");
        }
        return s.toString();
    }

    /** Lee el JSON de la IA y lo contrasta con los catálogos. Paquete para poder probarlo. */
    Map<String, Object> interpretarClasificacion(String respuesta,
                                                 List<com.mesaayuda.entity.Priority> prioridades,
                                                 List<com.mesaayuda.entity.Category> categorias,
                                                 List<com.mesaayuda.entity.SupportTeam> equipos) {
        return interpretarClasificacion(respuesta, prioridades, categorias, equipos, List.of());
    }

    /** Igual, contrastando también el tipo de ticket con su catálogo. */
    Map<String, Object> interpretarClasificacion(String respuesta,
                                                 List<com.mesaayuda.entity.Priority> prioridades,
                                                 List<com.mesaayuda.entity.Category> categorias,
                                                 List<com.mesaayuda.entity.SupportTeam> equipos,
                                                 List<com.mesaayuda.entity.TicketType> tipos) {
        JsonNode json = null;
        if (respuesta != null) {
            int inicio = respuesta.indexOf('{');
            int fin = respuesta.lastIndexOf('}');
            if (inicio >= 0 && fin > inicio) {
                try {
                    json = mapper.readTree(respuesta.substring(inicio, fin + 1));
                } catch (Exception e) {
                    json = null;
                }
            }
        }
        if (json == null) {
            throw new AiException("La IA no devolvió una clasificación legible.");
        }
        Map<String, Object> r = new java.util.LinkedHashMap<>();
        String pr = json.path("prioridad").asText(null);
        var prioridad = prioridades.stream().filter(p -> igual(p.getNamePriority(), pr)).findFirst().orElse(null);
        r.put("prioridad", prioridad == null ? null : prioridad.getNamePriority());
        r.put("prioridadId", prioridad == null ? null : prioridad.getIdPriority());
        String ca = json.path("categoria").asText(null);
        var categoria = categorias.stream().filter(c -> igual(c.getNameCategory(), ca)).findFirst().orElse(null);
        r.put("categoria", categoria == null ? null : categoria.getNameCategory());
        r.put("categoriaId", categoria == null ? null : categoria.getIdCategory());
        String ti = json.path("tipo").asText(null);
        var tipo = tipos.stream().filter(t -> igual(t.getNameTicketType(), ti)).findFirst().orElse(null);
        r.put("tipo", tipo == null ? null : tipo.getNameTicketType());
        r.put("tipoId", tipo == null ? null : tipo.getIdTicketType());
        String eq = json.path("equipo").asText(null);
        var equipo = equipos.stream().filter(t -> igual(t.getNameTeam(), eq)).findFirst().orElse(null);
        r.put("equipo", equipo == null ? null : equipo.getNameTeam());
        r.put("equipoId", equipo == null ? null : equipo.getIdTeam());
        String resumen = json.path("resumen").asText(null);
        r.put("resumen", resumen == null ? null : (resumen.length() > 1000 ? resumen.substring(0, 1000) : resumen));
        double confianza = json.path("confianza").asDouble(0);
        r.put("confianza", Math.max(0, Math.min(1, confianza)));
        return r;
    }

    /** Igualdad sin mayúsculas ni tildes: "Crítica" de la IA es la "CRITICA" del catálogo. */
    private static boolean igual(String catalogo, String propuesto) {
        if (catalogo == null || propuesto == null || propuesto.isBlank() || "null".equalsIgnoreCase(propuesto)) {
            return false;
        }
        return normalizarNombre(catalogo).equals(normalizarNombre(propuesto));
    }

    private static String normalizarNombre(String s) {
        return java.text.Normalizer.normalize(s.trim(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace('_', ' ').toUpperCase(java.util.Locale.ROOT);
    }

    /**
     * Chat libre con el asistente IA (usado por el asistente flotante de la aplicación).
     */
    public String chat(String message) {
        return chat(message, "anonimo", null);
    }

    /**
     * Chat multi-turn con el asistente. Sigue el manual de la Interactions API de Google:
     * la conversación se mantiene en el servidor encadenando {@code previous_interaction_id}.
     * Si Google purga la conversación previa, se reintenta una vez sin contexto.
     *
     * @param message    mensaje del usuario
     * @param sessionKey identificador de conversación (página o usuario autenticado)
     * @param context    contexto opcional que se inyecta al prompt del modelo
     */
    public String chat(String message, String sessionKey, String context) {
        if (message == null || message.isBlank()) {
            throw new BadRequestException("El mensaje no puede estar vacío");
        }
        AiConfig cfg = enabledConfig();
        exigirAccion(cfg, "CHAT");

        String prompt = buildChatPrompt(message, context);
        String key = sessionKey == null || sessionKey.isBlank() ? "anonimo" : sessionKey;
        String previous = chatState.get(key);
        try {
            GeminiResult result = "GEMINI".equalsIgnoreCase(cfg.getProvider())
                    ? callGemini(cfg, prompt, previous)
                    : new GeminiResult(callOpenAiCompatible(cfg, prompt), null);
            if (result.interactionId() != null) {
                chatState.put(key, result.interactionId());
            }
            return result.text();
        } catch (AiException e) {
            if (previous != null && e.getMessage() != null
                    && e.getMessage().toLowerCase().contains("interaction")) {
                chatState.remove(key);
                return chat(message, key, context);
            }
            throw e;
        } catch (Exception e) {
            throw new AiException("No se pudo contactar al proveedor de IA: " + e.getMessage());
        }
    }

    /**
     * Estado público del asistente (sin datos sensibles) para el widget flotante.
     */
    public AiStatusResponse status() {
        AiConfig cfg = aiConfigRepository.findFirstByOrderByIdAiConfigAsc().orElse(null);
        if (cfg == null) {
            return new AiStatusResponse(false, null, null, null, "SUMMARY,ANSWER");
        }
        return new AiStatusResponse(
                Boolean.TRUE.equals(cfg.getEnabled()),
                cfg.getProvider(),
                cfg.getModel(),
                cfg.getProviderIcon(),
                cfg.getEnabledActions() == null ? "SUMMARY,ANSWER" : cfg.getEnabledActions());
    }

    private AiConfig enabledConfig() {
        AiConfig cfg = aiConfigRepository.findFirstByOrderByIdAiConfigAsc().orElse(null);
        if (cfg == null || !Boolean.TRUE.equals(cfg.getEnabled())
                || cfg.getApiKey() == null || cfg.getApiKey().isBlank()) {
            throw new AiException("El asistente IA está deshabilitado. Actívelo en Administración → Configuración IA.");
        }
        return cfg;
    }

    private String buildChatPrompt(String message, String context) {
        StringBuilder sb = new StringBuilder("Actúa como asistente especializado de una mesa de servicios (COAC). ")
                .append("Responde en español, de forma clara, directa y útil en Markdown. ")
                .append("Si la consulta requiere información de tickets o usuarios, ")
                .append("indica que debe consultarse desde el módulo correspondiente.");
        sb.append("\n\n").append(instruccionesPorRol(rolActual()));
        agregarReglas(sb);
        if (context != null && !context.isBlank()) {
            sb.append("\n\nCONTEXTO ACTUAL (debes usarlo para responder):\n").append(context);
        }
        return sb.append("\n\n").append(message).toString();
    }

    /** Rol de quien hace la consulta (USER si no se puede saber: lo más restrictivo). */
    private String rolActual() {
        try {
            var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null) {
                for (var a : auth.getAuthorities()) {
                    String r = a.getAuthority();
                    if (r != null && r.startsWith("ROLE_")) {
                        return r.substring(5);
                    }
                }
            }
        } catch (RuntimeException e) {
            // Sin sesión legible se trata como solicitante.
        }
        return "USER";
    }

    /**
     * Cómo responder según quién pregunta. Antes el chat hablaba igual a todos y
     * a un solicitante le daba indicaciones de agente (asignar, cambiar estados,
     * tocar servidores o configuraciones internas).
     */
    static String instruccionesPorRol(String rol) {
        String r = rol == null ? "USER" : rol.trim().toUpperCase(java.util.Locale.ROOT);
        return switch (r) {
            case "AGENT", "ADMIN", "SUPER_ADMIN" -> "QUIÉN PREGUNTA: personal de TI de la mesa de servicios ("
                    + r + "). Puedes dar diagnóstico técnico, pasos de resolución, comandos, comprobaciones y "
                    + "redactar respuestas para el solicitante. Distingue claramente lo que hace el agente de lo "
                    + "que debe hacer el usuario.";
            default -> "QUIÉN PREGUNTA: un usuario final (solicitante), no personal de TI. Dale solo pasos "
                    + "sencillos y seguros que pueda hacer por sí mismo sin permisos de administrador (reiniciar, "
                    + "revisar cables, cerrar y abrir sesión, comprobar el mensaje de error). No le des "
                    + "instrucciones de agente: no le digas cómo asignar, cambiar estados, prioridades o "
                    + "categorías de tickets, ni le des comandos de servidor, configuraciones de red, de Active "
                    + "Directory o de sistemas internos, ni procedimientos internos de TI. No redactes respuestas "
                    + "\"del agente\". Si el problema necesita al equipo de TI, dile que lo atenderán por su ticket "
                    + "o que cree uno con los datos necesarios.";
        };
    }

    /**
 * Comprueba que el administrador no haya apagado esta accion. Sin esto, ocultar
 * el boton en la interfaz no impedia seguir llamando al endpoint, y apagar el
 * chat no impidia seguir usandolo.
 */
private void exigirAccion(AiConfig cfg, String accion) {
        String habilitadas = cfg.getEnabledActions();
        if (habilitadas == null || habilitadas.isBlank()) {
            return;
        }
        boolean activa = false;
        for (String parte : habilitadas.split(",")) {
            if (parte.trim().equalsIgnoreCase(accion)) {
                activa = true;
                break;
            }
        }
        if (!activa) {
            throw new BadRequestException("La acción " + accion
                    + " está deshabilitada en la configuración del asistente");
        }
    }

    /**
     * Inserta las reglas de la institucion en el prompt.
     *
     * Van antes que las instrucciones de la accion y despues de la frase de
     * rol, porque si una regla dijera "responde siempre en ingles" debe ganar a
     * la base que dice "en espanol". Se le dice explicitamente que mandan sobre
     * el resto, porque el modelo da prioridad a la instruccion mas cercana al
     * final del prompt.
     *
     * Si el administrador no escribio nada, el prompt queda igual que antes: las
     * reglas son optativas y no anaden texto vacio en cada llamada.
     */
    private void agregarReglas(StringBuilder sb) {
        AiConfig cfg = null;
        try {
            cfg = aiConfigRepository.findFirstByOrderByIdAiConfigAsc().orElse(null);
        } catch (Exception e) {
            log.warn("No se pudieron leer las reglas de la IA: {}", e.getMessage());
        }
        if (cfg == null) return;
        String reglas = cfg.getRulesText();
        if (reglas == null || reglas.isBlank()) return;
        sb.append("\n\nREGLAS DE LA INSTITUCION (tienen prioridad sobre cualquier otra indicacion):\n")
                .append(reglas.trim());
    }

    private String buildPrompt(String action, UUID ticketId, String instructions) {
        TicketResponse ticket = ticketService.findById(ticketId);
        List<AnswerResponse> thread = ticketService.getAnswers(ticketId).stream()
                .filter(a -> !Boolean.TRUE.equals(a.internalAnswer()))
                .toList();

        StringBuilder sb = new StringBuilder();
        if ("SUMMARY".equalsIgnoreCase(action)) {
            sb.append("Actúa como analista de una mesa de servicios (COAC). ")
                    .append("Redacta un RESUMEN ejecutivo en español de este ticket de soporte en máximo 150 palabras, ")
                    .append("en Markdown con encabezados cortos. Organiza: situación, acciones realizadas y estado actual.\n\n");
        } else {
            sb.append("Actúa como agente de soporte de una mesa de servicios (COAC), con tono profesional, amable y directo en español. ")
                    .append("Redacta una REPUESTA DEL AGENTE DIRIGIDA AL SOLICITANTE, en Markdown (usa listas o pasos si aplica). ")
                    .append("No uses saludos genéricos repetidos ni frases de relleno. Máximo 300 palabras. ")
                    .append("Comunica avances reales usando SOLO la información del hilo; si falta información, propón qué datos pedir.\n\n");
        }

        agregarReglas(sb);
        sb.append("TICKET #").append(ticket.numberTicket()).append("\n");
        sb.append("Título: ").append(ticket.titleTicket()).append("\n");
        sb.append("Prioridad: ").append(ticket.priority())
                .append(" · Estado: ").append(ticket.status())
                .append(" · Categoría: ").append(ticket.category())
                .append(" · Área: ").append(ticket.area()).append("\n");
        sb.append("Solicitante: ").append(ticket.user()).append("\n");
        sb.append("Descripción:\n").append(plain(ticket.descriptionTicket())).append("\n");

        if (!thread.isEmpty()) {
            sb.append("\nHilo de respuestas (en orden cronológico):\n");
            int i = 1;
            for (AnswerResponse a : thread) {
                sb.append(i++).append(". [")
                        .append(a.userDisplayName() != null ? a.userDisplayName() : a.user())
                        .append("] ").append(plain(a.messageAnswer())).append("\n");
            }
        }

        if (instructions != null && !instructions.isBlank()) {
            sb.append("\nInstrucciones adicionales del agente:\n").append(instructions).append("\n");
        }

        if ("ANSWER".equalsIgnoreCase(action)) {
            sb.append("\nEmpieza la respuesta directamente, sin encabezados tipo 'Respuesta del agente'.");
        }
        return sb.toString();
    }

    /**
     * Parámetros para GroqCloud: respuestas precisas (temperatura baja, conviene
     * para clasificar), tope de tokens y, en los modelos gpt-oss, razonamiento
     * "low" para que respondan antes y gasten menos.
     */
    static String opcionesGroq(String modelo) {
        StringBuilder o = new StringBuilder("\"temperature\":0.2,\"max_completion_tokens\":")
                .append(GROQ_MAX_TOKENS);
        if (modelo != null && modelo.toLowerCase().contains("gpt-oss")) {
            o.append(",\"reasoning_effort\":\"low\"");
        }
        return o.toString();
    }

    /**
     * Cuenta una consulta del día y corta si se pasó del límite configurado.
     * Se cuenta en la base (una fila por día) para que valga con varias
     * instancias y tras reiniciar. Sin tabla o sin JDBC no limita.
     */
    private void consumirCupo(AiConfig cfg) {
        Integer limite = cfg.getDailyLimit();
        if (limite == null || limite <= 0 || jdbcTemplate == null) {
            return;
        }
        java.time.LocalDate hoy = java.time.LocalDate.now(java.time.ZoneId.of("America/Guayaquil"));
        Integer usadas;
        try {
            usadas = jdbcTemplate.queryForObject(
                    "INSERT INTO ai_daily_usage (day_usage, count_usage) VALUES (?, 1) "
                            + "ON CONFLICT (day_usage) DO UPDATE SET count_usage = ai_daily_usage.count_usage + 1 "
                            + "RETURNING count_usage",
                    Integer.class, hoy);
        } catch (RuntimeException e) {
            log.warn("No se pudo contar la consulta diaria de la IA: {}", e.getMessage());
            return;
        }
        if (usadas != null && usadas > limite) {
            throw new AiException("Se alcanzó el límite diario de MAPI (" + limite
                    + " consultas). Vuelve a estar disponible mañana.");
        }
    }

    private GeminiResult callGemini(AiConfig cfg, String prompt, String previousInteractionId) throws Exception {
        consumirCupo(cfg);
        String base = stripSlash(cfg.getBaseUrl() == null || cfg.getBaseUrl().isBlank()
                ? DEFAULT_GEMINI_BASE : cfg.getBaseUrl());
        String model = geminiModel(cfg);
        String url = base + "/interactions";

        StringBuilder json = new StringBuilder("{\"model\":")
                .append(mapper.writeValueAsString(model))
                .append(",\"input\":")
                .append(mapper.writeValueAsString(prompt));
        if (previousInteractionId != null && !previousInteractionId.isBlank()) {
            json.append(",\"previous_interaction_id\":")
                    .append(mapper.writeValueAsString(previousInteractionId));
        }
        json.append('}');

        String body = postJson(url, Map.of(
                "x-goog-api-key", secretCryptoService.decryptIfNeeded(cfg.getApiKey()),
                "Api-Revision", GEMINI_API_REVISION), json.toString());
        JsonNode node = mapper.readTree(body);

        String status = node.path("status").asText("");
        if ("failed".equalsIgnoreCase(status) || "error".equalsIgnoreCase(status)) {
            throw new AiException("La IA no pudo generar la respuesta. Estado: " + status);
        }

        String text = extractGeminiText(node);
        if (text == null || text.isBlank()) {
            throw new AiException("La IA no devolvió contenido. Intenta de nuevo.");
        }
        String interactionId = node.path("id").asText(null);
        return new GeminiResult(text.strip(), interactionId);
    }

    /** Resultado de una llamada a Gemini: texto generado y el id de la interacción para continuar la charla. */
    private record GeminiResult(String text, String interactionId) {}

    /**
     * Extrae el texto de la respuesta de la Interactions API de Gemini. Soporta tanto
     * la propiedad conveniente {@code output_text} como el formato por pasos
     * {@code steps[].model_output.content[].text}.
     */
    private String extractGeminiText(JsonNode node) {
        String direct = node.path("output_text").asText(null);
        if (direct != null && !direct.isBlank()) {
            return direct;
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode step : node.path("steps")) {
            if ("model_output".equals(step.path("type").asText())) {
                for (JsonNode content : step.path("content")) {
                    if ("text".equals(content.path("type").asText())) {
                        String t = content.path("text").asText("");
                        if (!t.isBlank()) {
                            sb.append(t).append("\n");
                        }
                    }
                }
            }
        }
        String out = sb.toString().strip();
        return out.isBlank() ? null : out;
    }

    private String callOpenAiCompatible(AiConfig cfg, String prompt) throws Exception {
        consumirCupo(cfg);
        boolean groq = "GROQ".equalsIgnoreCase(cfg.getProvider());
        String base = stripSlash(cfg.getBaseUrl() == null || cfg.getBaseUrl().isBlank()
                ? (groq ? DEFAULT_GROQ_BASE : DEFAULT_OPENAI_BASE) : cfg.getBaseUrl());
        String url = base + "/chat/completions";
        String modelo = cfg.getModel() == null || cfg.getModel().isBlank()
                ? (groq ? DEFAULT_GROQ_MODEL : "gpt-4o-mini") : cfg.getModel();

        String json = "{\"model\":" + mapper.writeValueAsString(modelo)
                + ",\"messages\":[{\"role\":\"system\",\"content\":\"Eres un asistente experto de una mesa de servicios COAC.\"},"
                + "{\"role\":\"user\",\"content\":" + mapper.writeValueAsString(prompt) + "}],"
                + (groq ? opcionesGroq(modelo) : "\"temperature\":0.7") + "}";

        String body = postJson(url, Map.of("Authorization", "Bearer " + secretCryptoService.decryptIfNeeded(cfg.getApiKey())), json);
        JsonNode node = mapper.readTree(body);
        String text = node.path("choices").path(0).path("message").path("content").asText(null);
        if (text == null || text.isBlank()) {
            throw new AiException("La IA no devolvió contenido. Intenta de nuevo.");
        }
        return text.strip();
    }

    private String postJson(String url, Map<String, String> extraHeaders, String json) throws Exception {
        // La URL base la configura un administrador. Sin esta comprobación,
        // apuntarla a un servicio interno convertiría cada pregunta enviada a la
        // IA en una petición desde el servidor a la red privada, y la respuesta
        // se devolvería al usuario que la hizo.
        if (!urlSafetyValidator.esUrlPublica(url)) {
            throw new AiException(
                    "La URL del proveedor de IA debe ser pública y empezar por http o https.");
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
        if (extraHeaders != null) {
            extraHeaders.forEach(builder::header);
        }
        HttpResponse<String> resp = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() >= 300) {
            throw new AiException(extractError(resp.body()));
        }
        return resp.body();
    }

    private String extractError(String body) {
        if (body == null || body.isBlank()) {
            return "El proveedor de IA respondió con un error.";
        }
        try {
            JsonNode node = mapper.readTree(body);
            JsonNode message = node.path("error").path("message");
            if (message.isMissingNode() || message.isNull() || message.asText().isBlank()) {
                return body.length() > 300 ? body.substring(0, 300) : body;
            }
            return "El proveedor de IA respondió: " + message.asText();
        } catch (Exception e) {
            return body.length() > 300 ? body.substring(0, 300) : body;
        }
    }

    private String plain(String html) {
        if (html == null) return "";
        String s = html
                .replaceAll("(?s)<[^>]*>", " ")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&amp;", "&")
                .replaceAll("\\s+", " ")
                .trim();
        return s.length() > 3000 ? s.substring(0, 3000) : s;
    }

    private String stripSlash(String base) {
        return base != null && base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    /**
     * Resuelve el modelo de Gemini que se usará. Normaliza los modelos deprecados
     * de las series 1.x y 2.x (Google les responde 404/error de disponibilidad)
     * al vigente por defecto.
     */
    private String geminiModel(AiConfig cfg) {
        String model = cfg.getModel();
        if (model == null || model.isBlank()) {
            return DEFAULT_GEMINI_MODEL;
        }
        String lower = model.toLowerCase();
        if (lower.startsWith("gemini-1.") || lower.startsWith("gemini-2.")) {
            log.warn("Modelo Gemini '{}' deprecado. Se usará en su lugar '{}'.", model, DEFAULT_GEMINI_MODEL);
            return DEFAULT_GEMINI_MODEL;
        }
        return model;
    }
}