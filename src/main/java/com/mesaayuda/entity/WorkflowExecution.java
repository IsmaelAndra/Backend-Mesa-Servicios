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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * Una ejecución: un recorrido concreto del grafo disparado por un evento.
 *
 * <p>Se apunta a una {@link WorkflowVersion} concreta y no a la vigente. Esa es
 * toda la diferencia entre un motor con historial creíble y uno que miente.
 *
 * <p>{@code correlationKey} merece atención: es la clave que corta los bucles y
 * por eso vive aquí y también indexada en {@link WorkflowEventDedup}, que es
 * donde la unicidad está garantizada por la base de datos.
 */
@Entity
@Table(name = "workflow_execution")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowExecution {

    public static final String ESTADO_PENDIENTE = "PENDIENTE";
    public static final String ESTADO_EJECUTANDO = "EJECUTANDO";
    /** Detenido en un nodo de espera hasta una fecha. */
    public static final String ESTADO_ESPERANDO = "ESPERANDO";
    /** Detenido esperando que alguien apruebe. */
    public static final String ESTADO_APROBACION = "APROBACION";
    public static final String ESTADO_COMPLETADO = "COMPLETADO";
    public static final String ESTADO_ERROR = "ERROR";
    public static final String ESTADO_CANCELADO = "CANCELADO";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_workflow_execution", updatable = false)
    private UUID idWorkflowExecution;
    @Column(name = "workflow_workflow", nullable = false)
    private UUID workflowWorkflow;

    /**
     * Id de la versión usada, no su número.
     *
     * <p>Guardar el número obligaría a resolver "qué versión era el 3" cuando ya
     * haya diez. Guardando el id no hay ambigüedad nunca.
     */
    @Column(name = "version_workflow", nullable = false)
    private UUID versionWorkflow;

    @Column(name = "trigger_type", nullable = false, length = 40)
    private String triggerType;

    @Column(name = "correlation_key", nullable = false, length = 120)
    private String correlationKey;

    /**
     * Evento original, congelado.
     *
     * <p>Se guarda entero aunque el ticket haya cambiado, para poder reconstruir
     * meses después quéihkan vio el workflow cuando decidió lo que decidió.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "trigger_payload", columnDefinition = "jsonb")
    private String triggerPayload;

    @Column(name = "status_execution", nullable = false, length = 20)
    private String statusExecution = ESTADO_PENDIENTE;

    @Column(name = "current_node_execution", length = 60)
    private String currentNodeExecution;

    /** Contexto vivo de variables, aplanado con claves de puntos. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "variables_execution", columnDefinition = "jsonb")
    private String variablesExecution;

    @JdbcTypeCode(SqlTypes.JSON)

    @Column(name = "result_execution", columnDefinition = "jsonb")
    private String resultExecution;

    @Column(name = "error_node_execution", length = 60)
    private String errorNodeExecution;

    /**
     * Mensaje del fallo.
     *
     * <p><b>Nunca se enseña al usuario final.</b> Solo aparece en el historial de
     * ejecuciones, que es de SUPER_ADMIN. Puede contener nombres de catálogo,
     * identificadores internos y fragmentos de consulta: es material de
     * diagnóstico, no un mensaje para el usuario.
     */
    @Column(name = "error_message_execution", length = 1000)
    private String errorMessageExecution;

    /**
     * Cascada: se hereda entre workflows encadenados.
     *
     * <p>A dispara B dispara C dispara D. Al superar el máximo se corta, que es lo
     * que impide que un par de workflows mal configurados se alimenten entre sí
     * para siempre.
     */
    @Column(name = "depth_execution", nullable = false)
    private Integer depthExecution = 0;

    /** NULL cuando la arrancó el sistema; informado cuando fue manual. */
    @Column(name = "started_by_execution")
    private UUID startedByExecution;

    @Column(name = "attempts_execution", nullable = false)
    private Integer attemptsExecution = 0;

    @Column(name = "started_at_execution")
    private OffsetDateTime startedAtExecution;

    @Column(name = "finished_at_execution")
    private OffsetDateTime finishedAtExecution;

    @Column(name = "created_at_execution", nullable = false, updatable = false)
    private OffsetDateTime createdAtExecution;

    /**
     * Puerto por el que debe seguir al retomarse tras una pausa (NEXT, APROBADO,
     * RECHAZADO, VENCIDO). Null mientras no vuelve de una pausa.
     */
    @Column(name = "resume_port_execution", length = 30)
    private String resumePortExecution;

    /** Tipo de entidad relacionada (TICKET, USUARIO) para enlazarla desde el historial. */
    @Column(name = "entity_type_execution", length = 30)
    private String entityTypeExecution;

    @Column(name = "entity_id_execution")
    private UUID entityIdExecution;

    /** Ejecución cuya acción originó esta; null si la originó un evento externo o una persona. */
    @Column(name = "origin_execution")
    private UUID originExecution;
    public static final String PUERTO_APROBADO = "APROBADO";
    public static final String PUERTO_RECHAZADO = "RECHAZADO";
    public static final String PUERTO_VENCIDO = "VENCIDO";
    /** Al retomar, vuelve a ejecutar el nodo de la pausa (reintento tras un fallo pasajero). */
    public static final String PUERTO_REINTENTO = "REINTENTO";

    /** ¿Está en un estado del que el worker pueda ocuparse ya? */
    public boolean esElegible() {
        return ESTADO_PENDIENTE.equals(statusExecution)
                || ESTADO_EJECUTANDO.equals(statusExecution);
    }

    public boolean termino() {
        return ESTADO_COMPLETADO.equals(statusExecution)
                || ESTADO_ERROR.equals(statusExecution)
                || ESTADO_CANCELADO.equals(statusExecution);
    }

    /** Detenido a la espera de un temporal o de una aprobación. */
    public boolean estaDetenido() {
        return ESTADO_ESPERANDO.equals(statusExecution)
                || ESTADO_APROBACION.equals(statusExecution);
    }
}