package com.mesaayuda.repository;

import java.util.UUID;

import com.mesaayuda.entity.Area;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AreaRepository extends JpaRepository<Area, UUID>, JpaSpecificationExecutor<Area> {
    Optional<Area> findByNameArea(String nameArea);
    boolean existsByNameArea(String nameArea);
    List<Area> findByDeletedAreaFalseOrderByPriorityLevelDesc();

    /** Áreas de tecnología ("Tecnología", "Tecnologías" o la antigua "TI"). */
    @org.springframework.data.jpa.repository.Query("SELECT a FROM Area a WHERE a.deletedArea = false "
            + "AND (lower(a.nameArea) LIKE 'tecnolog%' OR upper(a.nameArea) = 'TI') "
            + "ORDER BY CASE WHEN upper(a.nameArea) = 'TI' THEN 1 ELSE 0 END, a.createdAtArea")
    List<Area> areasDeTecnologia();

    /** El área de tecnología, se llame como se llame en cada instalación. */
    default Optional<Area> tecnologia() {
        return areasDeTecnologia().stream().findFirst();
    }
}