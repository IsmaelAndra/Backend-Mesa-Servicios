package com.mesaayuda.entity;
import java.util.UUID;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "ticket_rating", indexes = {
        @Index(name = "idx_rating_ticket", columnList = "ticket_id"),
        @Index(name = "idx_rating_user", columnList = "user_id"),
        @Index(name = "idx_rating_month", columnList = "evaluation_month"),
        @Index(name = "idx_rating_req", columnList = "requirements_met")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TicketRating {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_rating", updatable = false)
    private UUID idRating;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "requirements_met", nullable = false)
    private Integer requirementsMet;

    @Column(name = "all_requirements_met", nullable = false)
    private Boolean allRequirementsMet = false;

    @Column(name = "response_time_rating")
    private Integer responseTimeRating;

    @Column(name = "resolution_quality")
    private Integer resolutionQuality;

    @Column(name = "agent_communication")
    private Integer agentCommunication;

    @Column(name = "overall_satisfaction")
    private Integer overallSatisfaction;

    @Column(name = "comments", columnDefinition = "text")
    private String comments;

    @CreationTimestamp
    @Column(name = "rated_at", nullable = false, updatable = false)
    private OffsetDateTime ratedAt;

    @Column(name = "evaluation_month", nullable = false)
    private LocalDate evaluationMonth;
}