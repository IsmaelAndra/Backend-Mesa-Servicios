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
@Table(name = "answer")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Answer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_answer", updatable = false)
    private UUID idAnswer;
    @Column(name = "message_answer", nullable = false, columnDefinition = "text")
    private String messageAnswer;

    @Column(name = "internal_answer", nullable = false)
    private Boolean internalAnswer = false;

    @CreationTimestamp
    @Column(name = "created_at_answer", nullable = false, updatable = false)
    private OffsetDateTime createdAtAnswer;

    @UpdateTimestamp
    @Column(name = "updated_at_answer", nullable = false)
    private OffsetDateTime updatedAtAnswer;

    @Column(name = "deleted_answer", nullable = false)
    private Boolean deletedAnswer = false;

    /** Última edición del texto, o null si nunca se editó. */
    @Column(name = "edited_at_answer")
    private OffsetDateTime editedAtAnswer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_answer", nullable = false)
    private Ticket ticketAnswer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_answer", nullable = false)
    private User userAnswer;
}