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
 * Registro de eventos ya disparados, para no repetirlos.
 *
 * <p><b>Esta tabla es el muro de verdad contra los bucles, y no es
 * decorativa.</b> La garantía está en la restricción {@code UNIQUE
 * (workflow, correlation_key)} de la base de datos, no en el código: dos hilos que
 * Received el mismo evento a la vez intentarán insertar la misma clave, uno ganará
 * y el otro recibirá un error de clave duplicada.
 *
 * <p>Un "SELECT y si no existe, INSERT" no serviría. Entre el SELECT y el INSERT
 * cabe otro hilo, y los dos pasarían el SELECT. Con la restricción no hay ventana:
 * es el patrón de la reserva de una plaza, dos personas pueden elegir la misma a
 * la vez y solo una se queda con ella.
 *
 * <p>Lo que esta tabla NO evita es el ciclo A → B → A. Para eso están el límite de
 * profundidad y el tope de pasos: aquí se corta la repetición del <i>mismo</i>
 * evento, que es el caso común y el más ruidoso.
 */
@Entity
@Table(name = "workflow_event_dedup")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowEventDedup {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_event_dedup", updatable = false)
    private UUID idEventDedup;
    @Column(name = "workflow_workflow", nullable = false)
    private UUID workflowWorkflow;

    /**
     * Clave del evento, tal como la construye el disparador.
     *
     * <p>Incluye el tipo de evento y no solo el identificador del ticket. Eso es lo
     * que permite que "ticket creado" y "ticket actualizado" disparen el mismo
     * workflow sin que uno anule al otro.
     */
    @Column(name = "correlation_key", nullable = false, length = 120)
    private String correlationKey;

    /** Qué ejecución ganó esta vez. Permite seguir el rastro hacia atrás. */
    @Column(name = "execution_execution", nullable = false)
    private UUID executionExecution;
    @Column(name = "created_at_event_dedup", nullable = false, updatable = false)
    private OffsetDateTime createdAtEventDedup;
}