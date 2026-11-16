package com.mesaayuda.proyectos;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProyectoAreaRepository extends JpaRepository<ProyectoArea, UUID> {

    List<ProyectoArea> findByProyectoIdOrderByCreatedAtAsc(UUID proyectoId);

    Optional<ProyectoArea> findByProyectoIdAndAreaId(UUID proyectoId, UUID areaId);

    List<ProyectoArea> findByAreaIdAndEstadoOrderByCreatedAtDesc(UUID areaId, String estado);

    List<ProyectoArea> findByProyectoIdIn(Collection<UUID> proyectoIds);
}
