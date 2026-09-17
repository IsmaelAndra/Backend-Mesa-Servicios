package com.mesaayuda.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "sla_rule")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SlaRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_sla_rule", updatable = false)
    private Long idSlaRule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sla_policy_id", nullable = false)
    private SlaPolicy slaPolicy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "area_id", nullable = false)
    private Area area;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "priority_id", nullable = false)
    private Priority priority;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_ticket")
    private TicketType type;

    @Column(name = "active_sla_rule", nullable = false)
    private Boolean activeSlaRule = true;

    @CreationTimestamp
    @Column(name = "created_at_sla_rule", nullable = false, updatable = false)
    private OffsetDateTime createdAtSlaRule;

    @UpdateTimestamp
    @Column(name = "updated_at_sla_rule", nullable = false)
    private OffsetDateTime updatedAtSlaRule;
}