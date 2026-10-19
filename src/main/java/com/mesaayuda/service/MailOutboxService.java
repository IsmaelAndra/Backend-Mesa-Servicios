package com.mesaayuda.service;

import com.mesaayuda.dto.MailOutboxItemResponse;
import com.mesaayuda.dto.MailOutboxResumenResponse;
import com.mesaayuda.dto.MicrosoftGraphConfigResponse;
import com.mesaayuda.entity.MailOutbox;
import com.mesaayuda.exception.BadRequestException;
import com.mesaayuda.exception.ResourceNotFoundException;
import com.mesaayuda.repository.MailOutboxRepository;
import com.mesaayuda.repository.NotificationRepository;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Cola de correo (outbox). Encolar es una inserción en la misma transacción
 * que el hecho que provoca el correo; el envío lo hace
 * {@link com.mesaayuda.scheduler.ColaCorreoScheduler} al ritmo que permite el buzón.
 */
@Service
public class MailOutboxService {

    private static final Logger log = LoggerFactory.getLogger(MailOutboxService.class);
    private static final ZoneId ZONA = ZoneId.of("America/Guayaquil");
    /** Tipos cuyo cuerpo lleva una clave o un enlace de un solo uso: se borra al enviarse. */
    private static final Set<String> SENSIBLES = Set.of("CREACION_USUARIO", "RESET_PASSWORD");

    private final MailOutboxRepository repository;
    private final NotificationRepository notificationRepository;
    private final MicrosoftGraphConfigService graphConfigService;
    private final MailOutboxPausa pausa;

    @Value("${app.mail.outbox.max-por-minuto:25}")
    private int maxPorMinuto;

    @Value("${app.mail.outbox.max-intentos:6}")
    private int maxIntentos;

    public MailOutboxService(MailOutboxRepository repository, NotificationRepository notificationRepository,
                             MicrosoftGraphConfigService graphConfigService, MailOutboxPausa pausa) {
        this.repository = repository;
        this.notificationRepository = notificationRepository;
        this.graphConfigService = graphConfigService;
        this.pausa = pausa;
    }

    public int maxPorMinuto() {
        return Math.max(1, maxPorMinuto);
    }

    // ============================ ENCOLAR ============================

    /**
     * Deja un correo en la cola. Si el correo no está habilitado en la
     * configuración de Graph no se encola nada (igual que antes no se enviaba):
     * una cola que nunca se vacía solo confunde.
     */
    @Transactional
    public void encolar(String para, String asunto, String cuerpoHtml, String tipo, int prioridad,
                        UUID notificationId, UUID ticketId) {
        if (para == null || para.isBlank()) {
            return;
        }
        if (!correoHabilitado()) {
            log.debug("Correo deshabilitado: no se encola '{}' para {}", asunto, para);
            return;
        }
        MailOutbox m = new MailOutbox();
        m.setToMail(para.trim());
        m.setSubjectMail(recortar(asunto == null ? "" : asunto, 500));
        m.setBodyMail(cuerpoHtml == null ? "" : cuerpoHtml);
        m.setTypeMail(tipo == null ? null : recortar(tipo, 60));
        m.setPriorityMail(prioridad);
        m.setNextAttemptAt(OffsetDateTime.now());
        m.setNotificationId(notificationId);
        m.setTicketId(ticketId);
        repository.save(m);
    }

    public boolean correoHabilitado() {
        try {
            MicrosoftGraphConfigResponse c = graphConfigService.getConfig();
            return c.enabled() && c.mailEnabled() && c.mailFromAccount() != null && !c.mailFromAccount().isBlank();
        } catch (Exception e) {
            return false;
        }
    }

    // ============================ PROCESO DE ENVÍO ============================

    /** Correos enviados de verdad en el último minuto (los agrupados no cuentan: salieron en uno). */
    @Transactional(readOnly = true)
    public long enviadosUltimoMinuto() {
        return repository.countByStatusMailAndSentAtMailAfter(MailOutbox.ENVIADO, OffsetDateTime.now().minusSeconds(60));
    }

    /**
     * Reclama hasta {@code n} correos y, para los de prioridad normal, se lleva
     * también los demás pendientes de la misma persona (para poder agruparlos).
     * Todo queda en ENVIANDO con un bloqueo de 2 minutos.
     */
    @Transactional
    public List<MailOutbox> reclamar(int n) {
        repository.liberarBloqueosVencidos();
        List<MailOutbox> lote = new ArrayList<>(repository.bloquearPendientes(n));
        Set<UUID> ids = new HashSet<>();
        lote.forEach(m -> ids.add(m.getIdMail()));
        Set<String> vistos = new HashSet<>();
        for (MailOutbox m : new ArrayList<>(lote)) {
            if (m.getPriorityMail() < MailOutbox.PRIORIDAD_NORMAL || !vistos.add(m.getToMail().toLowerCase())) {
                continue;
            }
            for (MailOutbox extra : repository.bloquearDelMismoDestinatario(m.getToMail(), ids)) {
                if (ids.add(extra.getIdMail())) {
                    lote.add(extra);
                }
            }
        }
        OffsetDateTime hasta = OffsetDateTime.now().plusMinutes(2);
        for (MailOutbox m : lote) {
            m.setStatusMail(MailOutbox.ENVIANDO);
            m.setLockedUntilMail(hasta);
        }
        return repository.saveAll(lote);
    }

    /**
     * Si ya salió (o está en cola) un correo de ese tipo para esa persona y ese
     * ticket en los últimos minutos. Lo usa el aviso del chat para no mandar un
     * correo por cada mensaje.
     */
    @Transactional(readOnly = true)
    public boolean hayCorreoReciente(String to, String tipo, java.util.UUID ticketId, long minutos) {
        if (to == null || tipo == null || ticketId == null || minutos <= 0) {
            return false;
        }
        return repository.existsByToMailIgnoreCaseAndTypeMailAndTicketIdAndCreatedAtMailAfter(
                to.trim(), tipo, ticketId, OffsetDateTime.now().minusMinutes(minutos));
    }

    /** Marca enviados. Con {@code agrupados}, los demás salieron dentro del resumen del primero. */
    @Transactional
    public void marcarEnviados(MailOutbox principal, Collection<MailOutbox> agrupados) {
        OffsetDateTime ahora = OffsetDateTime.now();
        aplicarEnviado(principal, MailOutbox.ENVIADO, ahora);
        for (MailOutbox m : agrupados) {
            aplicarEnviado(m, MailOutbox.AGRUPADO, ahora);
        }
    }

    private void aplicarEnviado(MailOutbox m, String estado, OffsetDateTime ahora) {
        MailOutbox fila = repository.findById(m.getIdMail()).orElse(null);
        if (fila == null) {
            return;
        }
        fila.setStatusMail(estado);
        fila.setSentAtMail(ahora);
        fila.setLockedUntilMail(null);
        fila.setLastErrorMail(null);
        if (fila.getTypeMail() != null && SENSIBLES.contains(fila.getTypeMail())) {
            // Lleva una clave o un enlace de un solo uso: una vez entregado no
            // tiene por qué quedarse en la base de datos.
            fila.setBodyMail("[contenido retirado tras el envío]");
        }
        repository.save(fila);
        marcarNotificacion(fila.getNotificationId(), "SENT");
    }

    /**
     * Un fallo. Si fue porque Microsoft pidió esperar ({@code saturado}), vuelve
     * a la cola sin gastar un intento; si no, se reintenta con espera creciente
     * (1, 2, 4, 8, 16, 32 min) hasta agotar los intentos.
     */
    @Transactional
    public void marcarFallo(MailOutbox m, String error, boolean saturado) {
        MailOutbox fila = repository.findById(m.getIdMail()).orElse(null);
        if (fila == null) {
            return;
        }
        fila.setLockedUntilMail(null);
        fila.setLastErrorMail(recortar(error == null ? "Error desconocido" : error, 2000));
        if (saturado) {
            fila.setStatusMail(MailOutbox.PENDIENTE);
            fila.setNextAttemptAt(OffsetDateTime.now());
        } else {
            int intentos = fila.getAttemptsMail() + 1;
            fila.setAttemptsMail(intentos);
            if (intentos >= Math.max(1, maxIntentos)) {
                fila.setStatusMail(MailOutbox.FALLIDO);
                marcarNotificacion(fila.getNotificationId(), "FAILED");
            } else {
                fila.setStatusMail(MailOutbox.PENDIENTE);
                fila.setNextAttemptAt(OffsetDateTime.now().plusMinutes(1L << Math.min(intentos - 1, 5)));
            }
        }
        repository.save(fila);
    }

    /** Devuelve a la cola, sin tocar intentos, lo que se reclamó y no llegó a enviarse. */
    @Transactional
    public void devolver(Collection<MailOutbox> sinEnviar) {
        for (MailOutbox m : sinEnviar) {
            repository.findById(m.getIdMail()).ifPresent(fila -> {
                if (MailOutbox.ENVIANDO.equals(fila.getStatusMail())) {
                    fila.setStatusMail(MailOutbox.PENDIENTE);
                    fila.setLockedUntilMail(null);
                    repository.save(fila);
                }
            });
        }
    }

    private void marcarNotificacion(UUID id, String estado) {
        if (id == null) {
            return;
        }
        notificationRepository.findById(id).ifPresent(n -> {
            if ("SENT".equals(estado)) {
                n.setStatusNotification("SENT");
                n.setSentAt(OffsetDateTime.now());
            } else if (!"SENT".equals(n.getStatusNotification())) {
                n.setStatusNotification(estado);
            }
            notificationRepository.save(n);
        });
    }

    @Transactional
    public int purgar(int diasRetencion) {
        return repository.purgar(OffsetDateTime.now().minusDays(Math.max(1, diasRetencion)));
    }

    // ============================ PANEL ============================

    @Transactional(readOnly = true)
    public MailOutboxResumenResponse resumen() {
        OffsetDateTime ahora = OffsetDateTime.now();
        OffsetDateTime inicioDia = ahora.atZoneSameInstant(ZONA).truncatedTo(ChronoUnit.DAYS).toOffsetDateTime();
        long pendientes = repository.countByStatusMail(MailOutbox.PENDIENTE);
        Long antiguedad = repository.findFirstByStatusMailOrderByCreatedAtMailAsc(MailOutbox.PENDIENTE)
                .map(m -> Math.max(0, ChronoUnit.SECONDS.between(m.getCreatedAtMail(), ahora)))
                .orElse(null);
        String remitente = null;
        try {
            remitente = graphConfigService.getConfig().mailFromAccount();
        } catch (Exception ignorado) {
            // Sin configuración de Graph: el panel lo muestra como deshabilitado.
        }
        Instant pausaHasta = pausa.hasta();
        return new MailOutboxResumenResponse(
                correoHabilitado(),
                remitente,
                maxPorMinuto(),
                enviadosUltimoMinuto(),
                pausaHasta == null ? null : pausaHasta.atZone(ZONA).toOffsetDateTime(),
                pendientes,
                repository.countByStatusMail(MailOutbox.ENVIANDO),
                repository.countByStatusMail(MailOutbox.FALLIDO),
                repository.countByStatusMailAndSentAtMailAfter(MailOutbox.ENVIADO, inicioDia),
                repository.countByStatusMailAndSentAtMailAfter(MailOutbox.AGRUPADO, inicioDia),
                repository.countByStatusMailAndCreatedAtMailAfter(MailOutbox.FALLIDO, inicioDia),
                antiguedad,
                (long) Math.ceil((double) pendientes / maxPorMinuto()),
                porHora(ahora),
                repository.porTipo(ahora.minusHours(24)).stream()
                        .map(f -> new MailOutboxResumenResponse.TipoCantidad(String.valueOf(f[0]), numero(f[1])))
                        .toList());
    }

    /** Las 24 horas completas, incluidas las que no tuvieron correos (la gráfica no salta). */
    private List<MailOutboxResumenResponse.PuntoHora> porHora(OffsetDateTime ahora) {
        OffsetDateTime inicio = ahora.atZoneSameInstant(ZONA).truncatedTo(ChronoUnit.HOURS).minusHours(23)
                .toOffsetDateTime();
        Map<Instant, long[]> horas = new LinkedHashMap<>();
        for (int i = 0; i < 24; i++) {
            horas.put(inicio.plusHours(i).toInstant(), new long[4]);
        }
        for (Object[] f : repository.porHoraYEstado(inicio)) {
            Instant hora = instante(f[0]);
            long[] c = hora == null ? null : horas.get(hora.truncatedTo(ChronoUnit.HOURS));
            if (c == null) {
                continue;
            }
            long n = numero(f[2]);
            switch (String.valueOf(f[1])) {
                case MailOutbox.ENVIADO -> c[0] += n;
                case MailOutbox.AGRUPADO -> c[1] += n;
                case MailOutbox.FALLIDO -> c[2] += n;
                case MailOutbox.PENDIENTE, MailOutbox.ENVIANDO -> c[3] += n;
                default -> { }
            }
        }
        List<MailOutboxResumenResponse.PuntoHora> out = new ArrayList<>();
        horas.forEach((h, c) -> out.add(new MailOutboxResumenResponse.PuntoHora(
                h.atZone(ZONA).toOffsetDateTime(), c[0], c[1], c[2], c[3])));
        return out;
    }

    @Transactional(readOnly = true)
    public Page<MailOutboxItemResponse> listar(String estado, String q, int pagina, int tamano) {
        Specification<MailOutbox> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (estado != null && !estado.isBlank()) {
                ps.add(cb.equal(root.get("statusMail"), estado.trim().toUpperCase()));
            }
            if (q != null && q.trim().length() >= 2) {
                String like = "%" + q.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                ps.add(cb.or(cb.like(cb.lower(root.get("toMail")), like, '\\'),
                        cb.like(cb.lower(root.get("subjectMail")), like, '\\')));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
        PageRequest page = PageRequest.of(Math.max(0, pagina), Math.min(Math.max(tamano, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAtMail"));
        return repository.findAll(spec, page).map(MailOutboxService::aItem);
    }

    @Transactional
    public MailOutboxItemResponse reintentar(UUID id) {
        MailOutbox m = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Correo", "id", id));
        if (!MailOutbox.FALLIDO.equals(m.getStatusMail()) && !MailOutbox.CANCELADO.equals(m.getStatusMail())) {
            throw new BadRequestException("Solo se reintenta un correo fallido o cancelado");
        }
        if (m.getBodyMail() != null && m.getBodyMail().startsWith("[contenido retirado")) {
            throw new BadRequestException("Este correo ya no tiene contenido que reenviar");
        }
        m.setStatusMail(MailOutbox.PENDIENTE);
        m.setAttemptsMail(0);
        m.setNextAttemptAt(OffsetDateTime.now());
        m.setLastErrorMail(null);
        return aItem(repository.save(m));
    }

    @Transactional
    public MailOutboxItemResponse cancelar(UUID id) {
        MailOutbox m = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Correo", "id", id));
        if (!MailOutbox.PENDIENTE.equals(m.getStatusMail()) && !MailOutbox.FALLIDO.equals(m.getStatusMail())) {
            throw new BadRequestException("Solo se cancela un correo pendiente o fallido");
        }
        m.setStatusMail(MailOutbox.CANCELADO);
        m.setLockedUntilMail(null);
        return aItem(repository.save(m));
    }

    @Transactional
    public int reintentarFallidos() {
        return repository.reintentarFallidos();
    }

    private static MailOutboxItemResponse aItem(MailOutbox m) {
        return new MailOutboxItemResponse(m.getIdMail(), m.getToMail(), m.getSubjectMail(), m.getTypeMail(),
                m.getPriorityMail(), m.getStatusMail(), m.getAttemptsMail(), m.getLastErrorMail(),
                m.getCreatedAtMail(), m.getNextAttemptAt(), m.getSentAtMail());
    }

    private static long numero(Object o) {
        return o instanceof Number n ? n.longValue() : 0L;
    }

    private static Instant instante(Object o) {
        if (o instanceof Instant i) return i;
        if (o instanceof OffsetDateTime odt) return odt.toInstant();
        if (o instanceof java.time.ZonedDateTime z) return z.toInstant();
        if (o instanceof java.sql.Timestamp t) return t.toInstant();
        if (o instanceof java.time.LocalDateTime l) return l.atZone(ZoneId.systemDefault()).toInstant();
        return null;
    }

    private static String recortar(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
