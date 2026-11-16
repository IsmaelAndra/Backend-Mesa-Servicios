package com.mesaayuda.repository;

import com.mesaayuda.entity.ReporteUsuario;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReporteUsuarioRepository extends JpaRepository<ReporteUsuario, UUID> {

    @EntityGraph(attributePaths = {"propietario"})
    List<ReporteUsuario> findByDeletedFalseOrderBySistemaDescUpdatedAtDesc();

    /** Los suyos y los del sistema; los del sistema primero. */
    @EntityGraph(attributePaths = {"propietario"})
    @org.springframework.data.jpa.repository.Query("SELECT r FROM ReporteUsuario r LEFT JOIN r.propietario p "
            + "WHERE r.deleted = false AND (r.sistema = true OR p.idUser = :userId) "
            + "ORDER BY r.sistema DESC, r.updatedAt DESC")
    List<ReporteUsuario> propiosYDelSistema(@org.springframework.data.repository.query.Param("userId") UUID userId);
}
