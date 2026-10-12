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
 * Una versión del grafo de un workflow.
 *
 * <p><b>Inmutable desde que se publica.</b> Es la pieza que hace que editar un
 * proceso no rompa las automatizaciones que ya están corriendo: cada ejecución
 * guarda el id de la versión que arrancó, así que si hoy creas la v3, las que
 * empezaron con la v2 siguen haciendo exactamente lo mismo que si pausases el
 * workflow entero.
 *
 * <p>El coste de esta decisión es que corregir un workflow publicado obliga a crear
 * una versión nueva. Es el precio correcto: lo contrario sería un historial de
 * ejecuciones que dice "se ejecutó la v1" cuando el grafo que había entonces era
 * otro, y eso no sirve ni para auditar ni para depurar.
 *
 * <p>Convive con {@link Workflow#authorityScope} sin contradecirlo: el alcance lo
 * decide quién guarda la versión, no cuándo se publicó.
 */
@Entity
@Table(name = "workflow_version")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_workflow_version", updatable = false)
    private UUID idWorkflowVersion;
    @Column(name = "workflow_workflow", nullable = false)
    private UUID workflowWorkflow;

    @Column(name = "version_workflow", nullable = false)
    private Integer versionWorkflow;

    /**
     * Grafo completo en JSON: nodos, conectores y metadatos. Un documento por
     * versión.
     *
     * <p>Se guarda como {@code String} y no como entidad con tablas propias porque
     * el grafo se lee y se escribe entero, nunca se consulta por nodos individuales.
     * Normalizarlo en seis tablas daría seis joins para reconstruir algo que
     * siempre se usa junto.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "definition_workflow", nullable = false, columnDefinition = "jsonb")
    private String definitionWorkflow;

    /**
     * Si esta es la versión vigente.
     *
     * <p>La unicidad la garantiza un índice único parcial en base de datos: varias
     * versiones inactivas conviven sin problema, pero dos vigentes para el mismo
     * workflow son imposibles aunque dos peticiones lleguen a la vez.
     */
    @Column(name = "is_active_version", nullable = false)
    private Boolean isActiveVersion = false;

    /** Momento de publicación. NULL mientras sea un borrador. */
    @Column(name = "published_at_workflow")
    private OffsetDateTime publishedAtWorkflow;

    @Column(name = "created_by_workflow", nullable = false)
    private UUID createdByWorkflow;
    @Column(name = "created_at_workflow", nullable = false, updatable = false)
    private OffsetDateTime createdAtWorkflow;

    public boolean estaPublicada() {
        return publishedAtWorkflow != null;
    }
}