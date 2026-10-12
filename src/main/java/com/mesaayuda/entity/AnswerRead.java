package com.mesaayuda.entity;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Marca de que una persona abrio una respuesta. Una fila por respuesta y
 * persona: permite tanto el "solo enviado" como la lista de quien leyo.
 */
@Entity
@Table(name = "answer_read")
@Getter
@Setter
@NoArgsConstructor
public class AnswerRead {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_answer_read", updatable = false)
    private UUID idAnswerRead;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "answer_id", nullable = false)
    private Answer answer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "read_at", nullable = false)
    private OffsetDateTime readAt = OffsetDateTime.now();
}
