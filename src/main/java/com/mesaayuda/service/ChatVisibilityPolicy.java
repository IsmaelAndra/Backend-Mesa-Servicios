package com.mesaayuda.service;

import com.mesaayuda.entity.ChatConfig;
import com.mesaayuda.entity.IdsConfiguracion;
import com.mesaayuda.entity.User;
import com.mesaayuda.repository.ChatConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Decide quien puede ver los acuses de lectura del chat (el doble check y el
 * texto "Leido por ...").
 * <p>
 * Es una tabla de configuracion y no una constante porque es una decision del
 * negocio que cambia: hay instalaciones donde el solicitante no debe saber que
 * su mensaje ya fue abierto, y otras donde interesa que lo vea. Valores:
 * <ul>
 *   <li>{@code TODOS}: cualquiera con acceso al ticket.</li>
 *   <li>{@code TODOS_EXCEPTO_USUARIOS} / {@code PERSONAL}: el personal
 *       (super_admin, admin y agentes) pero no los usuarios finales.</li>
 *   <li>{@code ADMIN}: super_admin y admin.</li>
 *   <li>{@code SUPER_ADMIN}: solo super_admin.</li>
 *   <li>{@code AGENTES}: solo agentes.</li>
 * </ul>
 * <p>
 * Importante: esto solo controla lo que se MUESTRA. Las marcas de lectura se
 * siguen guardando siempre, para que si despues se amplia la visibilidad no
 * haya que reconstruir el historico. Ademas {@code SUPER_ADMIN} queda incluido
 * en {@code ADMIN} y en {@code PERSONAL}: si el super administrador dejara de ver
 * la informacion que administra, cualquier combinacion quedaria incompleta.
 */
@Service
public class ChatVisibilityPolicy {

    private static final Logger log = LoggerFactory.getLogger(ChatVisibilityPolicy.class);

    public static final String TODOS = "TODOS";
    public static final String TODOS_EXCEPTO_USUARIOS = "TODOS_EXCEPTO_USUARIOS";
    public static final String PERSONAL = "PERSONAL";
    public static final String ADMIN = "ADMIN";
    public static final String SUPER_ADMIN = "SUPER_ADMIN";
    public static final String AGENTES = "AGENTES";

    /** Valor por defecto si la tabla todavia no tiene fila. */
    private static final String POR_DEFECTO = TODOS_EXCEPTO_USUARIOS;

    /** Opciones que puede marcar la pantalla de administracion, con su texto. */
    public static final List<Opcion> OPCIONES = List.of(
            new Opcion(TODOS, "Todos los usuarios con acceso al ticket"),
            new Opcion(TODOS_EXCEPTO_USUARIOS, "Todo el personal, sin usuarios finales"),
            new Opcion(PERSONAL, "Solo el personal (super admin, admin y agentes)"),
            new Opcion(ADMIN, "Solo administradores (super admin y admin)"),
            new Opcion(SUPER_ADMIN, "Solo el super administrador"),
            new Opcion(AGENTES, "Solo los agentes"));

    private static final Set<String> VALIDOS = Set.of(
            TODOS, TODOS_EXCEPTO_USUARIOS, PERSONAL, ADMIN, SUPER_ADMIN, AGENTES);

    private final ChatConfigRepository repository;

    // Cache corta: el chat pide esta configuracion en cada carga de pagina y en
    // cada acuse de lectura, y no tiene sentido abrir una transaccion por eso.
    // Cinco segundos es imperceptible para quien cambia el ajuste y evita que un
    // refresco en bucle del chat martillee la base.
    private static final long CACHE_MS = 5_000L;

    private volatile String cacheCodigo;
    private volatile boolean cacheNombres = true;
    private volatile long cacheAt = 0L;

    public ChatVisibilityPolicy(ChatConfigRepository repository) {
        this.repository = repository;
    }

    /** Opcion de la lista de visibilidad, tal y como la pinta el selector. */
    public record Opcion(String valor, String texto) {
    }

    @Transactional(readOnly = true)
    public ChatConfig config() {
        long ahora = System.currentTimeMillis();
        if (ahora - cacheAt < CACHE_MS && cacheCodigo != null) {
            ChatConfig c = new ChatConfig();
            c.setReadVisibleTo(cacheCodigo);
            c.setShowReadByNames(cacheNombres);
            return c;
        }
        Optional<ChatConfig> found = repository.findTopByOrderByIdChatConfigAsc();
        String codigo = found.map(ChatConfig::getReadVisibleTo).orElse(POR_DEFECTO);
        boolean nombres = found.map(c -> c.getShowReadByNames() == null || c.getShowReadByNames())
                .orElse(true);

        // Un valor desconocido en la base (por ejemplo escrito a mano por error)
        // no puede dejar el chat sin acuses: se cae al valor por defecto.
        if (!VALIDOS.contains(codigo)) {
            log.warn("Valor desconocido en chat_config.read_visible_to='{}', se usa '{}'.",
                    codigo, POR_DEFECTO);
            codigo = POR_DEFECTO;
        }

        cacheCodigo = codigo;
        cacheNombres = nombres;
        cacheAt = ahora;

        ChatConfig c = new ChatConfig();
        c.setReadVisibleTo(codigo);
        c.setShowReadByNames(nombres);
        return c;
    }

    /** Si este usuario puede ver los acuses de lectura de los mensajes. */
    @Transactional(readOnly = true)
    public boolean puedeVer(User u) {
        if (u == null) {
            return false;
        }
        String rol = u.getRolUser() == null
                ? null
                : u.getRolUser().getNameRole().toUpperCase(Locale.ROOT);
        if (rol == null) {
            return false;
        }
        // El super administrador siempre entra: es quien administra el ajuste y
        // dejarla fuera haria que una combinacion nunca fuera completa.
        if ("SUPER_ADMIN".equals(rol)) {
            return true;
        }
        return switch (config().getReadVisibleTo()) {
            case TODOS -> true;
            case TODOS_EXCEPTO_USUARIOS, PERSONAL ->
                    "ADMIN".equals(rol) || "AGENT".equals(rol);
            case ADMIN -> "ADMIN".equals(rol);
            case AGENTES -> "AGENT".equals(rol);
            default -> false;
        };
    }

    /** Si ademas se muestran los nombres de quienes leyeron, o solo el recuento. */
    @Transactional(readOnly = true)
    public boolean muestraNombres() {
        return config().getShowReadByNames();
    }

    /**
     * Guarda el ajuste. Un valor fuera de la lista se rechaza en vez de
     * guardarse: la tabla tiene un CHECK, pero fallar al insertar por SQL crudo
     * dari��a un error incomprensible, y aqui el mensaje puede ser claro.
     */
    @Transactional
    public ChatConfig actualizar(String readVisibleTo, Boolean showReadByNames, UUID updatedBy) {
        if (readVisibleTo == null || readVisibleTo.isBlank()) {
            throw new IllegalArgumentException("Debe indicar quien puede ver los acuses de lectura");
        }
        String codigo = readVisibleTo.trim().toUpperCase(Locale.ROOT);
        if (!VALIDOS.contains(codigo)) {
            throw new IllegalArgumentException("Valor no permitido para la visibilidad: " + readVisibleTo);
        }

        ChatConfig c = repository.findTopByOrderByIdChatConfigAsc().orElseGet(() -> {
            ChatConfig nuevo = new ChatConfig();
            nuevo.setIdChatConfig(IdsConfiguracion.CHAT);
            return nuevo;
        });
        c.setReadVisibleTo(codigo);
        if (showReadByNames != null) {
            c.setShowReadByNames(showReadByNames);
        }
        c.setUpdatedBy(updatedBy);
        ChatConfig guardada = repository.save(c);
        invalidarCache();
        log.info("Visibilidad de acuses de lectura: {}, mostrar nombres={} (por {})",
                guardada.getReadVisibleTo(), guardada.getShowReadByNames(), updatedBy);
        return guardada;
    }

    public void invalidarCache() {
        cacheCodigo = null;
        cacheAt = 0L;
    }
}
