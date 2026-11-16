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
public interface ProyectoRepository extends JpaRepository<Proyecto, UUID> {

    List<Proyecto> findByDeletedFalseOrderByUpdatedAtDesc();

    /**
     * Proyectos que ve alguien que no es súper admin ni auditor: los de su área
     * (jefe), los de áreas que participan aceptadas (jefe) y aquellos de los que es miembro.
     * Quien no es jefe pasa {@code new UUID(0, 0)} como areaJefe (un uuid nulo
     * no se puede tipar en JPQL).
     */
    @Query("SELECT p FROM Proyecto p WHERE p.deleted = false AND ("
            + "p.areaId = :areaJefe OR EXISTS (SELECT 1 FROM ProyectoArea pa "
            + "  WHERE pa.proyectoId = p.idProyecto AND pa.areaId = :areaJefe AND pa.estado = 'ACEPTADA') "
            + "OR EXISTS (SELECT 1 FROM ProyectoMiembro m WHERE m.proyectoId = p.idProyecto AND m.userId = :userId)) "
            + "ORDER BY p.updatedAt DESC")
    List<Proyecto> visiblesPara(@Param("userId") UUID userId, @Param("areaJefe") UUID areaJefe);
}
