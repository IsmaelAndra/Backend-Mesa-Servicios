package com.mesaayuda.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Solicitud de acceso de emergencia a un ticket confidencial (V29): la pide el
 * súper admin o el auditor y la decide el jefe del área que lo atiende.
 */
@Entity
@Table(name = "acceso_confidencial")
@Getter
@Setter
@NoArgsConstructor
public class AccesoConfidencial {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String APROBADO = "APROBADO";
    public static final String RECHAZADO = "RECHAZADO";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_acceso", updatable = false)
    private UUID idAcceso;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_acceso", nullable = false)
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "solicitante_acceso", nullable = false)
    private User solicitante;

    @Column(name = "motivo_acceso", nullable = false, length = 500)
    private String motivo;

    @Column(name = "estado_acceso", nullable = false, length = 20)
    private String estado = PENDIENTE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decidido_por_acceso")
    private User decididoPor;

    @Column(name = "comentario_acceso", length = 500)
    private String comentario;

    @Column(name = "decidido_at_acceso")
    private OffsetDateTime decididoAt;

    @Column(name = "vence_at_acceso")
    private OffsetDateTime venceAt;

    @CreationTimestamp
    @Column(name = "created_at_acceso", nullable = false, updatable = false)
    private OffsetDateTime createdAtAcceso;

    /** Aprobado y todavía dentro de plazo. */
    public boolean vigente(OffsetDateTime ahora) {
        return APROBADO.equals(estado) && venceAt != null && venceAt.isAfter(ahora);
    }
}
