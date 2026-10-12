package com.mesaayuda.repository;

import java.util.UUID;

import com.mesaayuda.entity.Priority;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PriorityRepository extends JpaRepository<Priority, UUID> {
    Optional<Priority> findByNamePriority(String namePriority);
    Optional<Priority> findByLevelPriority(Integer levelPriority);
    boolean existsByNamePriority(String namePriority);

    @Query("SELECT p FROM Priority p WHERE p.deletedPriority = false ORDER BY p.levelPriority ASC")
    List<Priority> findAllActive();
}