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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Horario laboral de un día de la semana (V49). Lo usa el SLA de las políticas con horario laboral. */
@Entity
@Table(name = "horario_laboral")
@Getter
@Setter
@NoArgsConstructor
public class HorarioLaboral {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_horario", updatable = false)
    private UUID idHorario;

    /** 1 = lunes … 7 = domingo (ISO). */
    @Column(name = "dia_horario", nullable = false, unique = true)
    private Integer diaHorario;

    @Column(name = "inicio_horario", nullable = false)
    private LocalTime inicioHorario;

    @Column(name = "fin_horario", nullable = false)
    private LocalTime finHorario;

    @Column(name = "activo_horario", nullable = false)
    private Boolean activoHorario = true;

    @UpdateTimestamp
    @Column(name = "updated_at_horario", nullable = false)
    private OffsetDateTime updatedAtHorario;
}
