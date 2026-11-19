package com.mesaayuda.repository;

import com.mesaayuda.entity.Feriado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface FeriadoRepository extends JpaRepository<Feriado, UUID> {

    List<Feriado> findAllByOrderByFechaFeriadoAsc();

    boolean existsByFechaFeriado(LocalDate fecha);
}
