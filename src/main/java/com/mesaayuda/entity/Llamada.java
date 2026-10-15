package com.mesaayuda.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Orden de llamada entre dos extensiones de la central (V13).
 *
 * <p>La crea un agente desde el ticket y la ejecuta el puente de telefonía que
 * corre dentro de la red de la cooperativa: PENDIENTE → ENVIADA (el puente la
 * tomó) → REALIZADA o ERROR. Si el puente no la recoge a tiempo, VENCIDA.
 */
@Entity
@Table(name = "llamada")
@Getter
@Setter
@NoArgsConstructor
public class Llamada {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String ENVIADA = "ENVIADA";
    public static final String REALIZADA = "REALIZADA";
    public static final String ERROR = "ERROR";
    public static final String VENCIDA = "VENCIDA";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_llamada", updatable = false)
    private UUID idLlamada;

    @Column(name = "ticket_id")
    private UUID ticketId;

    @Column(name = "caller_user_id")
    private UUID callerUserId;

    @Column(name = "callee_user_id")
    private UUID calleeUserId;

    @Column(name = "caller_extension", nullable = false, length = 20)
    private String callerExtension;

    @Column(name = "callee_extension", nullable = false, length = 20)
    private String calleeExtension;

    @Column(name = "status_llamada", nullable = false, length = 20)
    private String statusLlamada = PENDIENTE;

    @Column(name = "error_llamada", length = 500)
    private String errorLlamada;

    @Column(name = "created_at_llamada", nullable = false)
    private OffsetDateTime createdAtLlamada;

    @Column(name = "updated_at_llamada", nullable = false)
    private OffsetDateTime updatedAtLlamada;
}
