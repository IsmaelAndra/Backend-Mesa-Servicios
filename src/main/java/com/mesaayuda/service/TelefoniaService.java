package com.mesaayuda.service;

import com.mesaayuda.entity.IdsConfiguracion;
import com.mesaayuda.entity.Llamada;
import com.mesaayuda.entity.TelefoniaConfig;
import com.mesaayuda.entity.Ticket;
import com.mesaayuda.entity.User;
import com.mesaayuda.exception.BadRequestException;
import com.mesaayuda.exception.ResourceNotFoundException;
import com.mesaayuda.repository.LlamadaRepository;
import com.mesaayuda.repository.TelefoniaConfigRepository;
import com.mesaayuda.repository.TicketRepository;
import com.mesaayuda.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Llamadas entre extensiones de la central Grandstream UCM.
 *
 * <p>La UCM solo se ve desde la red interna, así que este servicio no habla con
 * ella: guarda la orden y la entrega al <b>puente de telefonía</b> (carpeta
 * {@code puente-telefonia/}), que corre en un servidor interno, la recoge por
 * HTTPS de salida y llama a la API de la UCM ({@code dialExtension}). No se
 * abre ningún puerto.
 *
 * <p>Todo se configura en Administración → Telefonía ({@link TelefoniaConfig}):
 * la URL, el usuario y la clave de la API de la UCM (cifrada) y el token del
 * puente (solo su SHA-256). El puente descarga esa configuración con su token.
 */
@Service
public class TelefoniaService {

    private static final Logger log = LoggerFactory.getLogger(TelefoniaService.class);

    /** Una extensión son solo dígitos: nada que la UCM pueda interpretar como otra orden. */
    private static final Pattern EXTENSION = Pattern.compile("^[0-9]{1,10}$");
    private static final Pattern URL_UCM = Pattern.compile("^https?://[A-Za-z0-9.\\-]+(:[0-9]{1,5})?(/[A-Za-z0-9._\\-/]*)?$");
    /** El agente espera con el teléfono delante: si el puente no la toma en este tiempo, ya no sirve. */
    private static final long SEGUNDOS_PARA_RECOGER = 60;
    /** El puente informa el resultado en segundos; pasado esto, se da por fallida. */
    private static final long SEGUNDOS_PARA_RESULTADO = 120;
    /** Dos clics seguidos no generan dos llamadas. */
    private static final long SEGUNDOS_ANTI_DOBLE_CLIC = 10;
    /** El puente pregunta cada 2 s; si no se le ve en este tiempo, está desconectado. */
    public static final long SEGUNDOS_PUENTE_EN_LINEA = 30;
    /** No se escribe el "visto" en cada pregunta del puente, solo cada tanto o si cambia algo. */
    private static final long SEGUNDOS_ENTRE_LATIDOS = 15;
    public static final int INTERVALO_PUENTE_MS = 2000;
    private static final int LOTE = 5;
    private static final String MASCARA = "********";

    private final LlamadaRepository llamadaRepository;
    private final TelefoniaConfigRepository configRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final TicketAccessPolicy accessPolicy;
    private final AuditService auditService;
    private final SecretCryptoService crypto;
    private final SecureRandom aleatorio = new SecureRandom();

    public TelefoniaService(LlamadaRepository llamadaRepository,
                            TelefoniaConfigRepository configRepository,
                            TicketRepository ticketRepository,
                            UserRepository userRepository,
                            UserService userService,
                            TicketAccessPolicy accessPolicy,
                            AuditService auditService,
                            SecretCryptoService crypto) {
        this.llamadaRepository = llamadaRepository;
        this.configRepository = configRepository;
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.userService = userService;
        this.accessPolicy = accessPolicy;
        this.auditService = auditService;
        this.crypto = crypto;
    }

    // ============================ Configuración ============================

    public record ConfigVista(boolean enabled, String ucmUrl, String ucmUsuario, boolean claveConfigurada,
                              boolean tokenConfigurado, OffsetDateTime tokenCreado,
                              boolean puenteEnLinea, OffsetDateTime puenteVisto, String puenteVersion,
                              String puenteIp, Boolean puenteUcmOk, String puenteUcmError,
                              boolean habilitada) {}

    public record ConfigRequest(Boolean enabled, String ucmUrl, String ucmUsuario, String ucmClave) {}

    public record ConfigPuente(boolean enabled, String ucmUrl, String ucmUsuario, String ucmClave, int intervaloMs) {}

    public record LlamadaVista(UUID id, String estado, String error, String origen, String destino,
                               String agente, String solicitante, Long ticketNumero, UUID ticketId,
                               OffsetDateTime creada) {}

    private TelefoniaConfig config() {
        return configRepository.findById(IdsConfiguracion.TELEFONIA).orElseGet(() -> {
            TelefoniaConfig c = new TelefoniaConfig();
            c.setIdTelefoniaConfig(IdsConfiguracion.TELEFONIA);
            c.setEnabledTelefonia(false);
            c.setUpdatedAtTelefonia(OffsetDateTime.now());
            return c;
        });
    }

    /** Si el botón Llamar se puede usar: activada, con UCM configurada y con puente autorizado. */
    @Transactional(readOnly = true)
    public boolean habilitada() {
        return lista(config());
    }

    private static boolean lista(TelefoniaConfig c) {
        return Boolean.TRUE.equals(c.getEnabledTelefonia())
                && c.getTokenHash() != null
                && c.getUcmUrl() != null && c.getUcmUsuario() != null && c.getUcmClaveCifrada() != null;
    }

    @Transactional(readOnly = true)
    public ConfigVista vista() {
        TelefoniaConfig c = config();
        boolean enLinea = c.getPuenteVistoAt() != null
                && c.getPuenteVistoAt().isAfter(OffsetDateTime.now().minusSeconds(SEGUNDOS_PUENTE_EN_LINEA));
        return new ConfigVista(Boolean.TRUE.equals(c.getEnabledTelefonia()), c.getUcmUrl(), c.getUcmUsuario(),
                c.getUcmClaveCifrada() != null, c.getTokenHash() != null, c.getTokenCreadoAt(),
                enLinea, c.getPuenteVistoAt(), c.getPuenteVersion(), c.getPuenteIp(),
                c.getPuenteUcmOk(), c.getPuenteUcmError(), lista(c));
    }

    /** Guarda la configuración de la UCM. Una clave vacía o enmascarada conserva la anterior. */
    @Transactional
    public ConfigVista guardar(ConfigRequest req, UUID quien) {
        TelefoniaConfig c = config();
        if (req.ucmUrl() != null) {
            String url = req.ucmUrl().trim().replaceAll("/+$", "");
            if (!url.isEmpty() && !URL_UCM.matcher(url).matches()) {
                throw new BadRequestException("La URL de la UCM no es válida (ejemplo: https://192.168.1.235:8089/api)");
            }
            c.setUcmUrl(url.isEmpty() ? null : url);
        }
        if (req.ucmUsuario() != null) {
            String usuario = req.ucmUsuario().trim();
            if (usuario.length() > 120) {
                throw new BadRequestException("El usuario de la API es demasiado largo");
            }
            c.setUcmUsuario(usuario.isEmpty() ? null : usuario);
        }
        String clave = req.ucmClave();
        if (clave != null && !clave.isBlank() && !clave.trim().matches("\\*+")) {
            c.setUcmClaveCifrada(crypto.encrypt(clave));
        }
        if (req.enabled() != null) {
            if (req.enabled() && (c.getUcmUrl() == null || c.getUcmUsuario() == null || c.getUcmClaveCifrada() == null)) {
                throw new BadRequestException("Complete la URL, el usuario y la clave de la API antes de activar la telefonía");
            }
            c.setEnabledTelefonia(req.enabled());
        }
        c.setUpdatedBy(quien);
        c.setUpdatedAtTelefonia(OffsetDateTime.now());
        configRepository.save(c);
        return vista();
    }

    /**
     * Genera un token nuevo para el puente y devuelve el valor en claro una sola
     * vez. El anterior deja de valer en ese momento.
     */
    @Transactional
    public String generarToken(UUID quien) {
        byte[] bytes = new byte[32];
        aleatorio.nextBytes(bytes);
        String token = HexFormat.of().formatHex(bytes);
        TelefoniaConfig c = config();
        c.setTokenHash(sha256(token));
        c.setTokenCreadoAt(OffsetDateTime.now());
        c.setUpdatedBy(quien);
        c.setUpdatedAtTelefonia(OffsetDateTime.now());
        configRepository.save(c);
        return token;
    }

    @Transactional(readOnly = true)
    public List<LlamadaVista> recientes() {
        List<Llamada> lista = llamadaRepository.findAllByOrderByCreatedAtLlamadaDesc(PageRequest.of(0, 50));
        Map<UUID, String> nombres = new HashMap<>();
        Map<UUID, Long> numeros = new HashMap<>();
        for (Llamada l : lista) {
            for (UUID id : new UUID[]{l.getCallerUserId(), l.getCalleeUserId()}) {
                if (id != null && !nombres.containsKey(id)) {
                    nombres.put(id, userRepository.findById(id).map(User::getFullName).orElse(null));
                }
            }
            if (l.getTicketId() != null && !numeros.containsKey(l.getTicketId())) {
                numeros.put(l.getTicketId(), ticketRepository.findById(l.getTicketId()).map(Ticket::getNumberTicket).orElse(null));
            }
        }
        return lista.stream().map(l -> new LlamadaVista(l.getIdLlamada(), l.getStatusLlamada(), l.getErrorLlamada(),
                l.getCallerExtension(), l.getCalleeExtension(),
                l.getCallerUserId() == null ? null : nombres.get(l.getCallerUserId()),
                l.getCalleeUserId() == null ? null : nombres.get(l.getCalleeUserId()),
                l.getTicketId() == null ? null : numeros.get(l.getTicketId()), l.getTicketId(),
                l.getCreatedAtLlamada())).toList();
    }

    public static boolean extensionValida(String extension) {
        return extension != null && EXTENSION.matcher(extension.trim()).matches();
    }

    // ============================ Desde la mesa ============================

    /**
     * El agente llama al solicitante del ticket: suena primero su extensión y,
     * al contestar, la central marca la del solicitante.
     */
    @Transactional
    public Llamada llamarAlSolicitante(UUID ticketId) {
        if (!habilitada()) {
            throw new BadRequestException("La telefonía no está configurada");
        }
        User actual = userService.getCurrentUser();
        Ticket ticket = ticketRepository.findById(ticketId)
                .filter(t -> !Boolean.TRUE.equals(t.getDeletedTicket()))
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", "id", ticketId));
        if (!accessPolicy.esPersonal(actual) || !accessPolicy.puedeLeer(ticket, actual)) {
            throw new AccessDeniedException("Solo el personal puede llamar desde un ticket");
        }
        User solicitante = ticket.getUserTicket();
        if (!extensionValida(actual.getExtensionUser())) {
            throw new BadRequestException("No tiene una extensión asignada. Pida que la registren en su usuario.");
        }
        if (solicitante == null || !extensionValida(solicitante.getExtensionUser())) {
            throw new BadRequestException("El solicitante no tiene una extensión registrada.");
        }
        String origen = actual.getExtensionUser().trim();
        String destino = solicitante.getExtensionUser().trim();
        if (origen.equals(destino)) {
            throw new BadRequestException("No puede llamarse a su propia extensión.");
        }

        OffsetDateTime ahora = OffsetDateTime.now();
        Llamada anterior = llamadaRepository.findTopByCallerUserIdOrderByCreatedAtLlamadaDesc(actual.getIdUser());
        if (anterior != null && destino.equals(anterior.getCalleeExtension())
                && (Llamada.PENDIENTE.equals(anterior.getStatusLlamada()) || Llamada.ENVIADA.equals(anterior.getStatusLlamada()))
                && anterior.getCreatedAtLlamada().isAfter(ahora.minusSeconds(SEGUNDOS_ANTI_DOBLE_CLIC))) {
            return anterior;
        }

        Llamada llamada = new Llamada();
        llamada.setTicketId(ticket.getIdTicket());
        llamada.setCallerUserId(actual.getIdUser());
        llamada.setCalleeUserId(solicitante.getIdUser());
        llamada.setCallerExtension(origen);
        llamada.setCalleeExtension(destino);
        llamada.setStatusLlamada(Llamada.PENDIENTE);
        llamada.setCreatedAtLlamada(ahora);
        llamada.setUpdatedAtLlamada(ahora);
        llamada = llamadaRepository.save(llamada);

        auditService.record(ticket, "LLAMADA", origen, destino,
                "Llamada de " + actual.getCorporateEmailUser() + " (ext. " + origen + ") a "
                        + solicitante.getCorporateEmailUser() + " (ext. " + destino + ")");
        log.info("Llamada {} pedida: ext. {} -> ext. {} (ticket {})", llamada.getIdLlamada(), origen, destino,
                ticket.getNumberTicket());
        return llamada;
    }

    /** Estado de una llamada; solo la ve quien la pidió o un administrador. */
    @Transactional(readOnly = true)
    public Llamada estado(UUID llamadaId) {
        User actual = userService.getCurrentUser();
        Llamada llamada = llamadaRepository.findById(llamadaId)
                .orElseThrow(() -> new ResourceNotFoundException("Llamada", "id", llamadaId));
        if (!actual.getIdUser().equals(llamada.getCallerUserId()) && !accessPolicy.esPersonalAdministrador(actual)) {
            throw new AccessDeniedException("No puede ver esta llamada");
        }
        return llamada;
    }

    // ============================ Desde el puente ============================

    /** Datos que el puente informa en cada consulta (cabeceras X-Puente-*). */
    public record Latido(String token, String ip, String version, String ucm) {}

    /** Configuración de la UCM para el puente, con la clave descifrada (viaja por HTTPS). */
    @Transactional
    public ConfigPuente configParaPuente(Latido latido) {
        TelefoniaConfig c = autenticar(latido);
        return new ConfigPuente(Boolean.TRUE.equals(c.getEnabledTelefonia()), c.getUcmUrl(), c.getUcmUsuario(),
                c.getUcmClaveCifrada() == null ? null : crypto.decrypt(c.getUcmClaveCifrada()), INTERVALO_PUENTE_MS);
    }

    /**
     * Entrega al puente las llamadas pendientes y las marca como ENVIADA. De paso
     * vence las que nadie recogió y cierra las que nunca devolvieron resultado.
     */
    @Transactional
    public List<Llamada> recogerPendientes(Latido latido) {
        TelefoniaConfig c = autenticar(latido);
        OffsetDateTime ahora = OffsetDateTime.now();
        llamadaRepository.vencerPendientes(ahora.minusSeconds(SEGUNDOS_PARA_RECOGER), ahora);
        llamadaRepository.cerrarEnviadasSinRespuesta(ahora.minusSeconds(SEGUNDOS_PARA_RESULTADO), ahora);
        if (!Boolean.TRUE.equals(c.getEnabledTelefonia())) {
            return List.of();
        }
        List<Llamada> lote = llamadaRepository.reclamarPendientes(LOTE);
        for (Llamada l : lote) {
            l.setStatusLlamada(Llamada.ENVIADA);
            l.setUpdatedAtLlamada(ahora);
        }
        return llamadaRepository.saveAll(lote);
    }

    /** El puente informa cómo terminó la orden en la UCM. */
    @Transactional
    public void informarResultado(Latido latido, UUID llamadaId, boolean ok, String error) {
        autenticar(latido);
        Llamada llamada = llamadaRepository.findById(llamadaId)
                .orElseThrow(() -> new ResourceNotFoundException("Llamada", "id", llamadaId));
        if (!Llamada.ENVIADA.equals(llamada.getStatusLlamada())) {
            return; // ya cerrada (vencida o informada dos veces)
        }
        llamada.setStatusLlamada(ok ? Llamada.REALIZADA : Llamada.ERROR);
        llamada.setErrorLlamada(ok ? null : recortar(error, "La central rechazó la llamada"));
        llamada.setUpdatedAtLlamada(OffsetDateTime.now());
        llamadaRepository.save(llamada);
        if (!ok) {
            log.warn("La UCM rechazó la llamada {}: {}", llamadaId, llamada.getErrorLlamada());
        }
    }

    /**
     * Comprueba el token (comparando hashes en tiempo constante) y anota que el
     * puente está vivo, con su versión, IP y el estado de su conexión con la UCM.
     */
    private TelefoniaConfig autenticar(Latido latido) {
        TelefoniaConfig c = config();
        String token = latido == null ? null : latido.token();
        if (c.getTokenHash() == null || token == null || token.isBlank()
                || !MessageDigest.isEqual(c.getTokenHash().getBytes(StandardCharsets.UTF_8),
                sha256(token.trim()).getBytes(StandardCharsets.UTF_8))) {
            throw new AccessDeniedException("Token del puente de telefonía inválido");
        }
        OffsetDateTime ahora = OffsetDateTime.now();
        Boolean ucmOk = latido.ucm() == null ? c.getPuenteUcmOk() : "ok".equalsIgnoreCase(latido.ucm().trim());
        String ucmError = latido.ucm() == null || Boolean.TRUE.equals(ucmOk) ? (Boolean.TRUE.equals(ucmOk) ? null : c.getPuenteUcmError())
                : recortar(latido.ucm(), null);
        boolean cambia = !Objects.equals(ucmOk, c.getPuenteUcmOk()) || !Objects.equals(ucmError, c.getPuenteUcmError())
                || !Objects.equals(recortarCorto(latido.version()), c.getPuenteVersion());
        if (cambia || c.getPuenteVistoAt() == null || c.getPuenteVistoAt().isBefore(ahora.minusSeconds(SEGUNDOS_ENTRE_LATIDOS))) {
            c.setPuenteVistoAt(ahora);
            c.setPuenteVersion(recortarCorto(latido.version()));
            c.setPuenteIp(recortarCorto(latido.ip()));
            c.setPuenteUcmOk(ucmOk);
            c.setPuenteUcmError(ucmError);
            configRepository.save(c);
        }
        return c;
    }

    static String sha256(String texto) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String recortar(String texto, String porDefecto) {
        if (texto == null || texto.isBlank()) {
            return porDefecto;
        }
        String t = texto.trim();
        return t.length() > 500 ? t.substring(0, 500) : t;
    }

    private static String recortarCorto(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String t = texto.trim();
        return t.length() > 40 ? t.substring(0, 40) : t;
    }

    /** Para mostrar que hay una clave guardada sin revelarla. */
    public static String mascara() {
        return MASCARA;
    }
}
