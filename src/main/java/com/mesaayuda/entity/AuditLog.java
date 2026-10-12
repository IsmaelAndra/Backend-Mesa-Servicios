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
@Table(name = "audit_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_audit", updatable = false)
    private UUID idAudit;
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "username", length = 150)
    private String username;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "entity", length = 50)
    private String entity;

    @Column(name = "entity_id")
    private UUID entityId;
    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    /**
     * Que cambio exactamente, por ejemplo "ACTIVE -> INACTIVE" o el rol antes y
     * despues. Sin esto la auditoria solo dice "algo cambio" y no permite
     * reconstruir un ataque ni distinguir un cambio de rol de uno de telefono.
     */
    @Column(name = "details", columnDefinition = "text")
    private String details;

    /** Metodo HTTP de la peticion que produjo el evento. */
    @Column(name = "http_method", length = 10)
    private String httpMethod;

    /**
     * Ruta de la peticion. Es lo que permite detectar un recorrido de ids: una
     * cuenta que pide /tickets/17, /tickets/18, /tickets/19 deja una huella
     * consultable agrupando por usuario y contando rutas distintas.
     */
    @Column(name = "request_uri", length = 300)
    private String requestUri;

    /**
     * Identificador de peticion, el mismo que RequestIdFilter devuelve en la
     * cabecera X-Request-Id y pone en el log. Une una fila de esta tabla con su
     * linea de log sin depender de marcas de tiempo.
     */
    @Column(name = "request_id", length = 64)
    private String requestId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}