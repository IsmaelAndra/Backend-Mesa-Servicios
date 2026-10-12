package com.mesaayuda.entity;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Una ejecución detenida en un nodo que no puede continuar.
 *
 * <p>Es la pieza que permite esperar 24 horas o la aprobación de un jefe sin
 * ocupar un hilo ni un temporizador en memoria. El worker no mira la ejecución
 * hasta que esta fila sea elegible.
 *
 * <p>La alternativa —un {@code Thread.sleep} o un {@code CompletableFuture} con
 * delay— parece más simple y es la razón por la que estos motores se caen: el
 * proceso se reinicia y se pierde lo que estuviera esperando, sin dejar rastro. Con
 * una fila en base, lo que estaba esperando sigue esperando.
 */
@Entity
@Table(name = "workflow_wait")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowWait {

    public static final String TIPO_DELAY = "DELAY";
    public static final String TIPO_UNTIL = "UNTIL";
    public static final String TIPO_APPROVAL = "APPROVAL";

    public static final String ESTADO_PENDIENTE = "PENDING";
    public static final String ESTADO_APROBADA = "APPROVED";
    public static final String ESTADO_RECHAZADA = "REJECTED";
    public static final String ESTADO_TIMEOUT = "TIMEOUT";

    /** Comentario que marca una espera creada para reintentar un nodo que falló. */
    public static final String MOTIVO_REINTENTO = "REINTENTO";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_wait", updatable = false)
    private UUID idWait;
    @Column(name = "execution_execution", nullable = false)
    private UUID executionExecution;

    @Column(name = "node_id_wait", nullable = false, length = 60)
    private String nodeIdWait;

    /**
     * DELAY: dormir un tiempo. UNTIL: esperar a una fecha. APPROVAL: esperar a
     * alguien.
     *
     * <p>Son tipos distintos y no se unifican porque el comportamiento del worker
     * es distinto: los dos primeros se resuelven por fecha, el tercero porque
     * alguien pulse un botón.
     */
    @Column(name = "kind_wait", nullable = false, length = 20)
    private String kindWait;

    /** Cuándo vuelve a ser elegible. NULL solo en APPROVAL. */
    @Column(name = "resume_at_wait")
    private OffsetDateTime resumeAtWait;

    /**
     * Quién puede aprobar: un rol o un usuario concreto.
     *
     * <p>Se comprueba en el momento de <b>decidir</b>, no al crear la espera. Si el
     * rol se comprueba al crear y luego cambia, una aprobación quedaría abierta a
     * quien ya no debería poder darla.
     */
    @Column(name = "approver_role_wait", length = 20)
    private String approverRoleWait;

    @Column(name = "approver_user_wait")
    private UUID approverUserWait;

    @Column(name = "status_wait", nullable = false, length = 20)
    private String statusWait = ESTADO_PENDIENTE;

    @Column(name = "decided_by_wait")
    private UUID decidedByWait;
    @Column(name = "decided_at_wait")
    private OffsetDateTime decidedAtWait;

    /**
     * Motivo del rechazo, o nota de quien aprueba.
     *
     * <p>Queda en el historial de la ejecución. No se envía a quien solicitó el
     * proceso: este comentario es para quien administra el workflow, no para
     * quien lo puso en marcha.
     */
    @Column(name = "comment_wait", length = 500)
    private String commentWait;

    @Column(name = "created_at_wait", nullable = false, updatable = false)
    private OffsetDateTime createdAtWait;

    /** ¿Toca al worker revisarla ahora mismo? */
    public boolean esElegibleAhora() {
        if (!ESTADO_PENDIENTE.equals(statusWait)) {
            return false;
        }
        if (TIPO_APPROVAL.equals(kindWait)) {
            return false;
        }
        return resumeAtWait != null && !resumeAtWait.isAfter(OffsetDateTime.now());
    }
}