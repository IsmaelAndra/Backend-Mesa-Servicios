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
@Table(name = "graph_config_audit")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GraphConfigAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_graph_audit", updatable = false)
    private UUID idGraphAudit;
    @Column(name = "config_id")
    private UUID configId;

    @Column(name = "changed_by")
    private UUID changedBy;
    @Column(name = "changed_by_email", length = 150)
    private String changedByEmail;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    /** Detalle legible de los campos modificados (nunca incluye el secreto). */
    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
