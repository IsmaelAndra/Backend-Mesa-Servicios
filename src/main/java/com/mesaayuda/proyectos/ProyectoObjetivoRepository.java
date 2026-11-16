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
public interface ProyectoObjetivoRepository extends JpaRepository<ProyectoObjetivo, UUID> {

    List<ProyectoObjetivo> findByProyectoIdAndDeletedFalseOrderByOrdenAscCreatedAtAsc(UUID proyectoId);
}
