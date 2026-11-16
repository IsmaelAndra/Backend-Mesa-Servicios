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
public interface ProyectoItemRepository extends JpaRepository<ProyectoItem, UUID> {

    List<ProyectoItem> findByProyectoIdAndDeletedFalseOrderByOrdenAscCreatedAtAsc(UUID proyectoId);

    List<ProyectoItem> findByProyectoIdInAndDeletedFalse(Collection<UUID> proyectoIds);

    List<ProyectoItem> findBySprintIdAndDeletedFalse(UUID sprintId);
}
