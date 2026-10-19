package com.mesaayuda.repository;

import com.mesaayuda.entity.MailOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MailOutboxRepository extends JpaRepository<MailOutbox, UUID>, JpaSpecificationExecutor<MailOutbox> {

    /**
     * Toma los siguientes pendientes y los deja bloqueados para esta transacción.
     * SKIP LOCKED: si otra instancia ya tiene una fila, se salta en vez de esperar.
     */
    @Query(value = "SELECT * FROM mail_outbox WHERE status_mail = 'PENDIENTE' AND next_attempt_at <= now() "
            + "ORDER BY priority_mail, created_at_mail LIMIT :n FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<MailOutbox> bloquearPendientes(@Param("n") int n);

    /** Otros avisos normales pendientes para la misma persona (para agruparlos en un resumen). */
    @Query(value = "SELECT * FROM mail_outbox WHERE status_mail = 'PENDIENTE' AND next_attempt_at <= now() "
            + "AND priority_mail >= 5 AND lower(to_mail) = lower(:para) AND id_mail NOT IN (:excluir) "
            + "ORDER BY created_at_mail LIMIT 50 FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<MailOutbox> bloquearDelMismoDestinatario(@Param("para") String para,
                                                  @Param("excluir") Collection<UUID> excluir);

    /** Si un proceso murió con correos en ENVIANDO, vuelven a la cola al vencer su bloqueo. */
    @Modifying
    @Query(value = "UPDATE mail_outbox SET status_mail = 'PENDIENTE', locked_until_mail = NULL "
            + "WHERE status_mail = 'ENVIANDO' AND locked_until_mail < now()", nativeQuery = true)
    int liberarBloqueosVencidos();

    long countByStatusMail(String status);

    /** Si a esa persona ya se le encoló un correo de ese tipo y ticket desde cierto momento. */
    boolean existsByToMailIgnoreCaseAndTypeMailAndTicketIdAndCreatedAtMailAfter(
            String toMail, String typeMail, java.util.UUID ticketId, java.time.OffsetDateTime desde);

    long countByStatusMailAndSentAtMailAfter(String status, OffsetDateTime desde);

    long countByStatusMailAndCreatedAtMailAfter(String status, OffsetDateTime desde);

    Optional<MailOutbox> findFirstByStatusMailOrderByCreatedAtMailAsc(String status);

    /** Por hora y estado, para la gráfica del panel. */
    @Query(value = "SELECT date_trunc('hour', COALESCE(sent_at_mail, created_at_mail)) AS hora, status_mail, count(*) "
            + "FROM mail_outbox WHERE created_at_mail >= :desde GROUP BY 1, 2 ORDER BY 1", nativeQuery = true)
    List<Object[]> porHoraYEstado(@Param("desde") OffsetDateTime desde);

    @Query(value = "SELECT COALESCE(type_mail, 'OTRO'), count(*) FROM mail_outbox "
            + "WHERE created_at_mail >= :desde GROUP BY 1 ORDER BY 2 DESC LIMIT 8", nativeQuery = true)
    List<Object[]> porTipo(@Param("desde") OffsetDateTime desde);

    @Modifying
    @Query(value = "UPDATE mail_outbox SET status_mail = 'PENDIENTE', attempts_mail = 0, next_attempt_at = now(), "
            + "last_error_mail = NULL WHERE status_mail = 'FALLIDO'", nativeQuery = true)
    int reintentarFallidos();

    /** Limpieza: lo ya resuelto y antiguo. Los pendientes y fallidos no se tocan. */
    @Modifying
    @Query(value = "DELETE FROM mail_outbox WHERE status_mail IN ('ENVIADO', 'AGRUPADO', 'CANCELADO') "
            + "AND created_at_mail < :limite", nativeQuery = true)
    int purgar(@Param("limite") OffsetDateTime limite);
}
