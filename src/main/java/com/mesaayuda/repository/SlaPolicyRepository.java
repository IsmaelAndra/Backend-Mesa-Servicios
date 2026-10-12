package com.mesaayuda.repository;

import java.util.UUID;

import com.mesaayuda.entity.SlaPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SlaPolicyRepository extends JpaRepository<SlaPolicy, UUID> {
    Optional<SlaPolicy> findByNameSlaPolicy(String nameSlaPolicy);
    boolean existsByNameSlaPolicy(String nameSlaPolicy);
    List<SlaPolicy> findByActiveSlaPolicyTrue();
}