package com.mesaayuda.repository;

import com.mesaayuda.entity.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StatusRepository extends JpaRepository<Status, Long> {
    Optional<Status> findByNameStatus(String nameStatus);
    boolean existsByNameStatus(String nameStatus);
}