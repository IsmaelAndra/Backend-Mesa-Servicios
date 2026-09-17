package com.mesaayuda.entity;

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
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_notification", updatable = false)
    private Long idNotification;

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

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @CreationTimestamp
    @Column(name = "created_at_notification", nullable = false, updatable = false)
    private OffsetDateTime createdAtNotification;
}