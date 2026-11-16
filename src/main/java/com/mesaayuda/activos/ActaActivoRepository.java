package com.mesaayuda.activos;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ActaActivoRepository extends JpaRepository<ActaActivo, UUID> {

    List<ActaActivo> findAllByOrderByCreatedAtDesc();

    List<ActaActivo> findByCustodioIdOrderByCreatedAtDesc(UUID custodioId);

    @Query("SELECT a FROM ActaActivo a WHERE EXISTS (SELECT 1 FROM ActaActivoItem i "
            + "WHERE i.actaId = a.idActa AND i.assetId = :assetId) ORDER BY a.createdAt DESC")
    List<ActaActivo> delActivo(@Param("assetId") UUID assetId);
}
