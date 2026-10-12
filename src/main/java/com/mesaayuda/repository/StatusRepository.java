package com.mesaayuda.repository;

import java.util.UUID;

import com.mesaayuda.entity.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StatusRepository extends JpaRepository<Status, UUID> {
    Optional<Status> findByNameStatus(String nameStatus);
    boolean existsByNameStatus(String nameStatus);
}