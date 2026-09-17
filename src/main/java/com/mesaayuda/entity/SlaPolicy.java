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
@Table(name = "sla_policy")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SlaPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_sla_policy", updatable = false)
    private Long idSlaPolicy;

    @Column(name = "name_sla_policy", nullable = false, unique = true, length = 100)
    private String nameSlaPolicy;

    @Column(name = "description_sla_policy")
    private String descriptionSlaPolicy;

    @Column(name = "response_time_minutes", nullable = false)
    private Integer responseTimeMinutes;

    @Column(name = "resolution_time_minutes", nullable = false)
    private Integer resolutionTimeMinutes;

    @Column(name = "active_sla_policy", nullable = false)
    private Boolean activeSlaPolicy = true;

    @CreationTimestamp
    @Column(name = "created_at_sla_policy", nullable = false, updatable = false)
    private OffsetDateTime createdAtSlaPolicy;

    @UpdateTimestamp
    @Column(name = "updated_at_sla_policy", nullable = false)
    private OffsetDateTime updatedAtSlaPolicy;
}