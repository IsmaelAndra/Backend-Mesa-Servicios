package com.mesaayuda.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "ticket_type")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TicketType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_ticket_type", updatable = false)
    private Long idTicketType;

    @Column(name = "name_ticket_type", nullable = false, unique = true, length = 100)
    private String nameTicketType;

    @Column(name = "description_ticket_type", length = 255)
    private String descriptionTicketType;

    @CreationTimestamp
    @Column(name = "created_at_ticket_type", nullable = false, updatable = false)
    private OffsetDateTime createdAtTicketType;

    @UpdateTimestamp
    @Column(name = "updated_at_ticket_type", nullable = false)
    private OffsetDateTime updatedAtTicketType;

    @Column(name = "deleted_ticket_type", nullable = false)
    private Boolean deletedTicketType = false;
}