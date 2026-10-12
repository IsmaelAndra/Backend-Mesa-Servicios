package com.mesaayuda.repository;

import java.util.UUID;

import com.mesaayuda.entity.MicrosoftGraphConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MicrosoftGraphConfigRepository extends JpaRepository<MicrosoftGraphConfig, UUID> {

    Optional<MicrosoftGraphConfig> findFirstByOrderByIdGraphConfigAsc();
}
