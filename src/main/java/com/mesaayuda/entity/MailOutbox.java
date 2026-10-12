package com.mesaayuda.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Un correo en la cola de salida (ver V10__cola_de_correo.sql). */
@Entity
@Table(name = "mail_outbox")
@Getter
@Setter
@NoArgsConstructor
public class MailOutbox {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String ENVIANDO = "ENVIANDO";
    public static final String ENVIADO = "ENVIADO";
    public static final String AGRUPADO = "AGRUPADO";
    public static final String FALLIDO = "FALLIDO";
    public static final String CANCELADO = "CANCELADO";

    /** Prioridad de lo que no puede esperar (cuentas nuevas, restablecer contraseña). */
    public static final int PRIORIDAD_URGENTE = 1;
    public static final int PRIORIDAD_NORMAL = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_mail", updatable = false)
    private UUID idMail;

    @Column(name = "to_mail", nullable = false, length = 320)
    private String toMail;

    @Column(name = "subject_mail", nullable = false, length = 500)
    private String subjectMail;

    @Column(name = "body_mail", nullable = false, columnDefinition = "text")
    private String bodyMail;

    @Column(name = "type_mail", length = 60)
    private String typeMail;

    @Column(name = "priority_mail", nullable = false)
    private Integer priorityMail = PRIORIDAD_NORMAL;

    @Column(name = "status_mail", nullable = false, length = 20)
    private String statusMail = PENDIENTE;

    @Column(name = "attempts_mail", nullable = false)
    private Integer attemptsMail = 0;

    @Column(name = "next_attempt_at", nullable = false)
    private OffsetDateTime nextAttemptAt;

    @Column(name = "locked_until_mail")
    private OffsetDateTime lockedUntilMail;

    @Column(name = "last_error_mail", columnDefinition = "text")
    private String lastErrorMail;

    @Column(name = "notification_id")
    private UUID notificationId;

    @Column(name = "ticket_id")
    private UUID ticketId;

    @CreationTimestamp
    @Column(name = "created_at_mail", nullable = false, updatable = false)
    private OffsetDateTime createdAtMail;

    @Column(name = "sent_at_mail")
    private OffsetDateTime sentAtMail;
}
