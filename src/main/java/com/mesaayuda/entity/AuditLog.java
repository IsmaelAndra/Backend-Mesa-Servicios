package com.mesaayuda.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "audit_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_audit", updatable = false)
    private Long idAudit;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "username", length = 150)
    private String username;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "entity", length = 50)
    private String entity;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}