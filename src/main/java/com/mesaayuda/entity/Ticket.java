package com.mesaayuda.entity;
import java.util.UUID;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "ticket", indexes = {
        @Index(name = "idx_ticket_user", columnList = "user_ticket"),
        @Index(name = "idx_ticket_agent", columnList = "agent_ticket"),
        @Index(name = "idx_ticket_status", columnList = "status_ticket"),
        @Index(name = "idx_ticket_created", columnList = "created_at_ticket")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_ticket", updatable = false)
    private UUID idTicket;

    /**
     * Número correlativo para las personas ("ticket 43"). Lo asigna la base con
     * su secuencia al insertar (V4__numero_ticket.sql); Hibernate no lo escribe y
     * lo relee tras el INSERT. La clave sigue siendo idTicket.
     */
    @Column(name = "number_ticket", insertable = false, updatable = false)
    @org.hibernate.annotations.Generated(event = org.hibernate.generator.EventType.INSERT)
    private Long numberTicket;
    @Column(name = "title_ticket", nullable = false, length = 255)
    private String titleTicket;

    @Column(name = "description_ticket", columnDefinition = "text")
    private String descriptionTicket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_ticket", nullable = false)
    private User userTicket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_ticket")
    private User agentTicket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_ticket")
    private SupportTeam teamTicket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "area_ticket", nullable = false)
    private Area areaTicket;

    /**
     * Sede a la que corresponde el caso. No es la del solicitante: alguien de
     * Quito puede abrir un ticket que hay que resolver en Guayaquil.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sucursal_id")
    private Sucursal sucursalTicket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_ticket", nullable = false)
    private Category categoryTicket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_ticket")
    private TicketType typeTicket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "status_ticket", nullable = false)
    private Status statusTicket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "priority_ticket", nullable = false)
    private Priority priorityTicket;

    @Column(name = "closing_date_ticket")
    private OffsetDateTime closingDateTicket;

    @CreationTimestamp
    @Column(name = "created_at_ticket", nullable = false, updatable = false)
    private OffsetDateTime createdAtTicket;

    @UpdateTimestamp
    @Column(name = "updated_at_ticket", nullable = false)
    private OffsetDateTime updatedAtTicket;

    @Column(name = "deleted_ticket", nullable = false)
    private Boolean deletedTicket = false;

    /**
     * La prioridad la fijó una persona (al crear o al gestionar el ticket). Las
     * automatizaciones no la cambian salvo que el nodo diga "forzar".
     */
    @Column(name = "priority_manual_ticket", nullable = false)
    private Boolean priorityManualTicket = false;
}