package com.mesaayuda.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Persona autorizada a ver un ticket confidencial (V29). */
@Entity
@Table(name = "ticket_autorizado")
@Getter
@Setter
@NoArgsConstructor
public class TicketAutorizado {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_ticket_autorizado", updatable = false)
    private UUID idTicketAutorizado;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_ticket_autorizado", nullable = false)
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_ticket_autorizado", nullable = false)
    private User usuario;

    @CreationTimestamp
    @Column(name = "created_at_ticket_autorizado", nullable = false, updatable = false)
    private OffsetDateTime createdAtTicketAutorizado;

    public TicketAutorizado(Ticket ticket, User usuario) {
        this.ticket = ticket;
        this.usuario = usuario;
    }
}
