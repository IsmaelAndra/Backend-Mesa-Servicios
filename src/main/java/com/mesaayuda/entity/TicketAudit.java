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
@Table(name = "ticket_audit", indexes = {
        @Index(name = "idx_audit_ticket", columnList = "ticket_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TicketAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_audit", updatable = false)
    private UUID idAudit;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "action_type", nullable = false, length = 50)
    private String actionType;

    @Column(name = "old_value", columnDefinition = "text")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "text")
    private String newValue;

    @Column(name = "metadata", columnDefinition = "text")
    private String metadata;

    @CreationTimestamp
    @Column(name = "created_at_audit", nullable = false, updatable = false)
    private OffsetDateTime createdAtAudit;
}