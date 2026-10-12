package com.mesaayuda.entity;
import java.util.UUID;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "notification", indexes = {
        @Index(name = "idx_notif_status", columnList = "status_notification")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_notification", updatable = false)
    private UUID idNotification;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id")
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "type_notification", nullable = false, length = 50)
    private String typeNotification;

    @Column(name = "subject_notification", nullable = false, length = 255)
    private String subjectNotification;

    @Column(name = "message_notification", nullable = false, columnDefinition = "text")
    private String messageNotification;

    @Column(name = "status_notification", nullable = false, length = 30)
    private String statusNotification = "PENDING";

    /**
     * Si el usuario ya abrió la notificación. Es independiente del estado de
     * envío: marcar como leída no debe alterar si el correo se entrega o no.
     */
    @Column(name = "read_notification", nullable = false)
    private Boolean readNotification = false;

    @Column(name = "read_at_notification")
    private OffsetDateTime readAtNotification;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @CreationTimestamp
    @Column(name = "created_at_notification", nullable = false, updatable = false)
    private OffsetDateTime createdAtNotification;
}