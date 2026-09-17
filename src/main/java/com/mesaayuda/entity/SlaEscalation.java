package com.mesaayuda.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "sla_escalation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SlaEscalation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_escalation", updatable = false)
    private Long idEscalation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sla_policy_id", nullable = false)
    private SlaPolicy slaPolicy;

    @Column(name = "trigger_percentage", nullable = false)
    private Integer triggerPercentage;

    @Column(name = "action_type", nullable = false, length = 50)
    private String actionType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_team_id")
    private SupportTeam destinationTeam;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_user_id")
    private User destinationUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notification_template_id")
    private NotificationTemplate notificationTemplate;

    @CreationTimestamp
    @Column(name = "created_at_escalation", nullable = false, updatable = false)
    private OffsetDateTime createdAtEscalation;
}