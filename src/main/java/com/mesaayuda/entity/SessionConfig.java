package com.mesaayuda.entity;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

/**
 * Duracion de la sesion. Se guarda una sola fila (id 1) y la cambia un
 * administrador desde la pantalla de seguridad, de modo que el tiempo de vida
 * del token no quede fijo en el yml.
 */
@Entity
@Table(name = "session_config")
@Getter
@Setter
@NoArgsConstructor
public class SessionConfig {

    // Sin GeneratedValue a proposito: la tabla es un singleton con
    // DEFAULT 1 (igual que routing_config), asi que el id se fija siempre en 1
    // y el seed no depende del valor siguiente de una secuencia.
    @Id
    @Column(name = "id_session_config", updatable = false)
    private UUID idSessionConfig = IdsConfiguracion.SESION;
    /** Minutos de vida del token de acceso. */
    @Column(name = "session_minutes", nullable = false)
    private Integer sessionMinutes = 15;

    /** Desde cuantos minutos antes del vencimiento se avisa al usuario. */
    @Column(name = "warning_minutes", nullable = false)
    private Integer warningMinutes = 5;

    /** Si es true, el frontend renueva solo el token mientras haya actividad. */
    @Column(name = "auto_renew", nullable = false)
    private Boolean autoRenew = true;

    /**
     * Si el cierre por inactividad esta activo. Con esto apagado la sesion dura
     * los minutos de session_minutes sin importar si el usuario esta o no
     * mirando la pantalla.
     */
    @Column(name = "idle_enabled", nullable = false)
    private Boolean idleEnabled = true;

    /** Minutos sin actividad antes de cerrar la sesion. */
    @Column(name = "idle_minutes", nullable = false)
    private Integer idleMinutes = 15;

    @Column(name = "updated_by")
    private UUID updatedBy;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
