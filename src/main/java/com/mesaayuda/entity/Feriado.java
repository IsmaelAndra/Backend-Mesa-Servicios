package com.mesaayuda.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Día no laborable para el SLA (V49). {@code anual}: se repite cada año en el mismo día y mes. */
@Entity
@Table(name = "feriado")
@Getter
@Setter
@NoArgsConstructor
public class Feriado {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_feriado", updatable = false)
    private UUID idFeriado;

    @Column(name = "fecha_feriado", nullable = false, unique = true)
    private LocalDate fechaFeriado;

    @Column(name = "nombre_feriado", nullable = false, length = 120)
    private String nombreFeriado;

    @Column(name = "anual_feriado", nullable = false)
    private Boolean anualFeriado = false;

    @CreationTimestamp
    @Column(name = "created_at_feriado", nullable = false, updatable = false)
    private OffsetDateTime createdAtFeriado;
}
