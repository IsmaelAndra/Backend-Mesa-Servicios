package com.mesaayuda.repository;

import com.mesaayuda.entity.HorarioLaboral;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HorarioLaboralRepository extends JpaRepository<HorarioLaboral, UUID> {

    List<HorarioLaboral> findAllByOrderByDiaHorarioAsc();

    Optional<HorarioLaboral> findByDiaHorario(Integer dia);
}
