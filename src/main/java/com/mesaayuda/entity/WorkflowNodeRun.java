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
 * El paso a paso de una ejecución: una fila por nodo recorrido.
 *
 * <p>Sin esta tabla, depurar una automatización es adivinar. El estado final y el
 * error no dicen <i>dónde</i> se rompió ni <i>con qué datos</i> llegó el nodo, que
 * es justo lo que hace falta cuando un workflow lleva tres meses en producción y
 * de pronto se comporta distinto.
 *
 * <p>{@code OMITIDO} merece explicación: un nodo no se ejecuta cuando la rama que
 * tomó el grafo no pasa por él. No es un fallo, pero se registra para que el
 * recorrido se lea entero y no parezca que falta un paso.
 */
@Entity
@Table(name = "workflow_node_run")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowNodeRun {

    public static final String ESTADO_EJECUTANDO = "EJECUTANDO";
    public static final String ESTADO_COMPLETADO = "COMPLETADO";
    public static final String ESTADO_ERROR = "ERROR";
    public static final String ESTADO_OMITIDO = "OMITIDO";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_node_run", updatable = false)
    private UUID idNodeRun;
    @Column(name = "execution_execution", nullable = false)
    private UUID executionExecution;
    /**
     * Id del nodo dentro del grafo, no su tipo.
     *
     * <p>Hace falta el id y no solo el tipo porque un grafo puede tener dos nodos
     * NOTIFY distintos, y en el historial tienen que poder distinguirse.
     */
    @Column(name = "node_id_node_run", nullable = false, length = 60)
    private String nodeIdNodeRun;

    @Column(name = "node_type_node_run", nullable = false, length = 40)
    private String nodeTypeNodeRun;

    @Column(name = "status_node_run", nullable = false, length = 20)
    private String statusNodeRun = ESTADO_EJECUTANDO;

    /** Variables tal como entraron al nodo. Es lo que explica una decisión rara. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_node_run", columnDefinition = "jsonb")
    private String inputNodeRun;

    /** Lo que el nodo devolvió, incluidas las variables que aportó. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output_node_run", columnDefinition = "jsonb")
    private String outputNodeRun;

    /** Solo para diagnóstico; nunca se muestra al usuario final. */
    @Column(name = "error_message_node_run", length = 1000)
    private String errorMessageNodeRun;

    /** Orden dentro de la ejecución, para reconstruir el recorrido. */
    @Column(name = "sequence_node_run", nullable = false)
    private Integer sequenceNodeRun = 0;

    @Column(name = "started_at_node_run")
    private OffsetDateTime startedAtNodeRun;

    @Column(name = "finished_at_node_run")
    private OffsetDateTime finishedAtNodeRun;

    @Column(name = "created_at_node_run", nullable = false, updatable = false)
    private OffsetDateTime createdAtNodeRun;
}