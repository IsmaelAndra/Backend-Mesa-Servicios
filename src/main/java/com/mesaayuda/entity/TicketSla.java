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
@Table(name = "ticket_sla")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TicketSla {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_ticket_sla", updatable = false)
    private UUID idTicketSla;
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false, unique = true)
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sla_policy_id", nullable = false)
    private SlaPolicy slaPolicy;

    @Column(name = "response_due_at")
    private OffsetDateTime responseDueAt;

    @Column(name = "resolution_due_at")
    private OffsetDateTime resolutionDueAt;

    @Column(name = "response_completed_at")
    private OffsetDateTime responseCompletedAt;

    @Column(name = "resolution_completed_at")
    private OffsetDateTime resolutionCompletedAt;

    @Column(name = "response_breached", nullable = false)
    private Boolean responseBreached = false;

    @Column(name = "resolution_breached", nullable = false)
    private Boolean resolutionBreached = false;

    @Column(name = "paused_at")
    private OffsetDateTime pausedAt;

    @Column(name = "total_paused_minutes", nullable = false)
    private Integer totalPausedMinutes = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}