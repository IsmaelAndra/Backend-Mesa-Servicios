package com.mesaayuda.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "monthly_evaluation", indexes = {
        @Index(name = "idx_eval_agent", columnList = "agent_id"),
        @Index(name = "idx_eval_month", columnList = "evaluation_month"),
        @Index(name = "idx_eval_status", columnList = "status"),
        @Index(name = "idx_eval_score", columnList = "satisfaction_score")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_evaluation", updatable = false)
    private Long idEvaluation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private User agent;

    @Column(name = "evaluation_month", nullable = false)
    private LocalDate evaluationMonth;

    @Column(name = "tickets_closed", nullable = false)
    private Integer ticketsClosed = 0;

    @Column(name = "avg_response_time_minutes", precision = 10, scale = 2)
    private BigDecimal avgResponseTimeMinutes;

    @Column(name = "avg_resolution_time_minutes", precision = 10, scale = 2)
    private BigDecimal avgResolutionTimeMinutes;

    @Column(name = "sla_compliance_rate", precision = 5, scale = 2)
    private BigDecimal slaComplianceRate;

    @Column(name = "avg_requirements_met", precision = 3, scale = 2)
    private BigDecimal avgRequirementsMet;

    @Column(name = "avg_response_time_rating", precision = 3, scale = 2)
    private BigDecimal avgResponseTimeRating;

    @Column(name = "avg_resolution_quality", precision = 3, scale = 2)
    private BigDecimal avgResolutionQuality;

    @Column(name = "avg_agent_communication", precision = 3, scale = 2)
    private BigDecimal avgAgentCommunication;

    @Column(name = "avg_overall_satisfaction", precision = 3, scale = 2)
    private BigDecimal avgOverallSatisfaction;

    @Column(name = "requirements_met_count")
    private Integer requirementsMetCount = 0;

    @Column(name = "satisfaction_score", precision = 3, scale = 2)
    private BigDecimal satisfactionScore;

    @Column(name = "supervisor_rating")
    private Integer supervisorRating;

    @Column(name = "supervisor_comments", columnDefinition = "text")
    private String supervisorComments;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evaluated_by")
    private User evaluatedBy;

    @Column(name = "status", nullable = false, length = 30)
    private String status = "PENDING";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}