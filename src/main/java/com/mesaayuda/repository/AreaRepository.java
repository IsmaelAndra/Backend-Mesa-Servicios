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
}