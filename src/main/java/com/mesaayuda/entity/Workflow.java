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
 * Un proceso automatizado.
 *
 * <p>La fila es la identidad y el estado operativo. El grafo de nodos NO vive
 * aquí sino en {@link WorkflowVersion}: si se guardara en esta tabla, editar un
 * workflow alteraría en silencio las ejecuciones que ya estaban en marcha.
 *
 * <p>Los estados significan cosas distintas y conviene no confundirlos:
 * <ul>
 *   <li><b>BORRADOR</b>: se está construyendo, no se ejecuta aunque tenga trigger.
 *   <li><b>ACTIVO</b>: se ejecuta.
 *   <li><b>INACTIVO</b>: pausado a propósito. Se distingue de BORRADOR en que
 *       conserva una versión publicada, y al reactivarlo sigue ejecutando el
 *       mismo grafo.
 *   <li><b>ERROR</b>: el motor lo marca. Se reserva para una definición que ha
 *       dejado de ser válida (por ejemplo, se borró un catálogo del que depende),
 *       no para un fallo puntual de una ejecución.
 * </ul>
 */
@Entity
@Table(name = "workflow")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Workflow {

    public static final String ESTADO_BORRADOR = "BORRADOR";
    public static final String ESTADO_ACTIVO = "ACTIVO";
    public static final String ESTADO_INACTIVO = "INACTIVO";
    public static final String ESTADO_ERROR = "ERROR";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_workflow", updatable = false)
    private UUID idWorkflow;
    @Column(name = "name_workflow", nullable = false, length = 120)
    private String nameWorkflow;

    @Column(name = "description_workflow", length = 500)
    private String descriptionWorkflow;

    @Column(name = "status_workflow", nullable = false, length = 20)
    private String statusWorkflow = ESTADO_BORRADOR;

    /**
     * Tipo de disparador: {@code TICKET_CREATED}, {@code SCHEDULE}...
     *
     * <p>Lo decide el registro de disparadores, no esta entidad. Añadir un tipo
     * nuevo es añadir una entrada al catálogo, no tocar esta clase.
     */
    @Column(name = "trigger_type", length = 40)
    private String triggerType;

    /**
     * Parámetros del disparador, en JSONB.
     *
     * <p>Qué hay dentro depende del tipo: para {@code SCHEDULE}, un cron y una
     * zona; para un evento de ticket, qué cambios concretos desataran la ejecución.
     * Se deja como texto porque cada disparador necesita una forma distinta y
     * forzarles una tabla común sería más rígido que guardarlo suelto.
     *
     * <p>El campo es un {@code String} y no un {@code Map} a propósito: el motor
     * lo trata como documento opaco y no necesita interpretarlo. Con
     * {@link SqlTypes#JSON} es Hibernate quien lo serializa, porque una columna
     * {@code jsonb} rechaza cualquier texto que no sea JSON válido y sin esta
     * anotación un disparador con parámetros en texto plano fallaría al guardarse.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "trigger_config", columnDefinition = "jsonb")
    private String triggerConfig;

    /**
     * Versión vigente, copiada aquí para no contar versiones activas en cada
     * lectura del constructor.
     */
    @Column(name = "current_version_workflow", nullable = false)
    private Integer currentVersion = 1;

    /**
     * Limite de autoridad con el que se ejecutan sus acciones.
     *
     * <p><b>Es la pieza de seguridad mas importante de esta tabla.</b> Un workflow
     * escribe en tickets, cambia estados y notifica, así que el motor tiene que
     * saber con qué permisos hacerlo. La respuesta es: los que tenía su autor al
     * guardarlo, nunca más. Si un ADMIN creara un workflow, ese workflow no podría
     * tocar la cuenta de un SUPER_ADMIN ni pasar por encima de
     * {@code exigirSuperAdminParaSuperAdmin}. Así es como un proceso automatizado
     * respeta el sistema de permisos en vez de rodearlo.
     *
     * <p>Se recalcula al guardar y NO se puede editar a mano desde la API.
     */
    @Column(name = "authority_scope", nullable = false, length = 20)
    private String authorityScope = "SUPER_ADMIN";

    @Column(name = "created_by_workflow", nullable = false)
    private UUID createdByWorkflow;

    @Column(name = "updated_by_workflow")
    private UUID updatedByWorkflow;
    @Column(name = "last_run_workflow")
    private OffsetDateTime lastRunWorkflow;

    /** Próxima ejecución de un disparador programado; null en los demás. */
    @Column(name = "next_run_at_workflow")
    private OffsetDateTime nextRunAtWorkflow;

    @Column(name = "run_count_workflow", nullable = false)
    private Long runCountWorkflow = 0L;

    @Column(name = "created_at_workflow", nullable = false, updatable = false)
    private OffsetDateTime createdAtWorkflow;

    @Column(name = "updated_at_workflow", nullable = false)
    private OffsetDateTime updatedAtWorkflow;

    @Column(name = "deleted_workflow", nullable = false)
    private Boolean deletedWorkflow = false;

    public boolean estaActivo() {
        return ESTADO_ACTIVO.equals(statusWorkflow);
    }

    public boolean esBorrador() {
        return ESTADO_BORRADOR.equals(statusWorkflow);
    }
}