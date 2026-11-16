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
public interface ProyectoMiembroRepository extends JpaRepository<ProyectoMiembro, UUID> {

    List<ProyectoMiembro> findByProyectoIdOrderByCreatedAtAsc(UUID proyectoId);

    Optional<ProyectoMiembro> findByProyectoIdAndUserId(UUID proyectoId, UUID userId);

    boolean existsByProyectoIdAndUserId(UUID proyectoId, UUID userId);

    List<ProyectoMiembro> findByProyectoIdIn(Collection<UUID> proyectoIds);
}
