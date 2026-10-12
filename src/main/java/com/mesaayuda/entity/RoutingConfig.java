package com.mesaayuda.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Configuración global de asignación automática de tickets.
 * Tabla de fila única (id siempre 1) administrable por el SUPER_ADMIN.
 */
@Entity
@Table(name = "routing_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RoutingConfig {

    @Id
    @Column(name = "id_routing_config")
    private UUID id = IdsConfiguracion.ROUTING;

    /** Si es false, ningún ticket se asigna automáticamente al crearse. */
    @Column(name = "auto_assign_enabled", nullable = false)
    private Boolean autoAssignEnabled = true;

    /** Si es true, se avisa al usuario cuando el ticket queda sin agente. */
    @Column(name = "notify_unassigned", nullable = false)
    private Boolean notifyUnassigned = false;

    @UpdateTimestamp
    @Column(name = "updated_at_routing_config", nullable = false)
    private OffsetDateTime updatedAtRoutingConfig;
}
