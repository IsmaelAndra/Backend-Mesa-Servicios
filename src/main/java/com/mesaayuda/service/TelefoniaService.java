package com.mesaayuda.service;

import com.mesaayuda.entity.Llamada;
import com.mesaayuda.entity.Ticket;
import com.mesaayuda.entity.User;
import com.mesaayuda.exception.BadRequestException;
import com.mesaayuda.exception.ResourceNotFoundException;
import com.mesaayuda.repository.LlamadaRepository;
import com.mesaayuda.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Llamadas entre extensiones de la central Grandstream UCM.
 *
 * <p>La UCM solo se ve desde la red interna, así que este servicio no habla con
 * ella: guarda la orden y la entrega al <b>puente de telefonía</b> (carpeta
 * {@code puente-telefonia/}), que corre en un servidor interno, la recoge por
 * HTTPS y llama a la API de la UCM ({@code dialExtension}). No se abre ningún
 * puerto: el puente solo hace conexiones de salida.
 *
 * <p>El puente se identifica con {@code TELEFONIA_PUENTE_TOKEN}. Si la variable
 * está vacía, la telefonía queda apagada y el botón Llamar no aparece.
 */
@Service
public class TelefoniaService {

    private static final Logger log = LoggerFactory.getLogger(TelefoniaService.class);

    /** Una extensión son solo dígitos: nada que la UCM pueda interpretar como otra orden. */
    private static final Pattern EXTENSION = Pattern.compile("^[0-9]{1,10}$");
    /** El agente espera con el teléfono delante: si el puente no la toma en este tiempo, ya no sirve. */
    private static final long SEGUNDOS_PARA_RECOGER = 60;
    /** El puente informa el resultado en segundos; pasado esto, se da por fallida. */
    private static final long SEGUNDOS_PARA_RESULTADO = 120;
    /** Dos clics seguidos no generan dos llamadas. */
    private static final long SEGUNDOS_ANTI_DOBLE_CLIC = 10;
    private static final int LOTE = 5;

    private final LlamadaRepository llamadaRepository;
    private final TicketRepository ticketRepository;
    private final UserService userService;
    private final TicketAccessPolicy accessPolicy;
    private final AuditService auditService;
    private final byte[] tokenPuente;

    public TelefoniaService(LlamadaRepository llamadaRepository,
                            TicketRepository ticketRepository,
                            UserService userService,
                            TicketAccessPolicy accessPolicy,
                            AuditService auditService,
                            @Value("${app.telefonia.puente-token:}") String tokenPuente) {
        this.llamadaRepository = llamadaRepository;
        this.ticketRepository = ticketRepository;
        this.userService = userService;
        this.accessPolicy = accessPolicy;
        this.auditService = auditService;
        this.tokenPuente = tokenPuente == null ? new byte[0] : tokenPuente.trim().getBytes(StandardCharsets.UTF_8);
    }

    /** Si hay puente configurado. Sin él, el botón Llamar no se muestra. */
    public boolean habilitada() {
        return tokenPuente.length >= 32;
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

    /**
     * Entrega al puente las llamadas pendientes y las marca como ENVIADA. De paso
     * vence las que nadie recogió y cierra las que nunca devolvieron resultado.
     */
    @Transactional
    public List<Llamada> recogerPendientes(String token) {
        comprobarToken(token);
        OffsetDateTime ahora = OffsetDateTime.now();
        llamadaRepository.vencerPendientes(ahora.minusSeconds(SEGUNDOS_PARA_RECOGER), ahora);
        llamadaRepository.cerrarEnviadasSinRespuesta(ahora.minusSeconds(SEGUNDOS_PARA_RESULTADO), ahora);
        List<Llamada> lote = llamadaRepository.reclamarPendientes(LOTE);
        for (Llamada l : lote) {
            l.setStatusLlamada(Llamada.ENVIADA);
            l.setUpdatedAtLlamada(ahora);
        }
        return llamadaRepository.saveAll(lote);
    }

    /** El puente informa cómo terminó la orden en la UCM. */
    @Transactional
    public void informarResultado(String token, UUID llamadaId, boolean ok, String error) {
        comprobarToken(token);
        Llamada llamada = llamadaRepository.findById(llamadaId)
                .orElseThrow(() -> new ResourceNotFoundException("Llamada", "id", llamadaId));
        if (!Llamada.ENVIADA.equals(llamada.getStatusLlamada())) {
            return; // ya cerrada (vencida o informada dos veces)
        }
        llamada.setStatusLlamada(ok ? Llamada.REALIZADA : Llamada.ERROR);
        llamada.setErrorLlamada(ok ? null : recortar(error));
        llamada.setUpdatedAtLlamada(OffsetDateTime.now());
        llamadaRepository.save(llamada);
        if (!ok) {
            log.warn("La UCM rechazó la llamada {}: {}", llamadaId, llamada.getErrorLlamada());
        }
    }

    /** Comparación en tiempo constante: el tiempo de respuesta no revela cuántos caracteres acertó. */
    private void comprobarToken(String token) {
        if (!habilitada() || token == null
                || !MessageDigest.isEqual(tokenPuente, token.trim().getBytes(StandardCharsets.UTF_8))) {
            throw new AccessDeniedException("Token del puente de telefonía inválido");
        }
    }

    private static String recortar(String texto) {
        if (texto == null || texto.isBlank()) {
            return "La central rechazó la llamada";
        }
        String t = texto.trim();
        return t.length() > 500 ? t.substring(0, 500) : t;
    }
}
