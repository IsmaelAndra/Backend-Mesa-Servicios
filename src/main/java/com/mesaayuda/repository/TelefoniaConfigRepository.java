package com.mesaayuda.repository;

import com.mesaayuda.entity.TelefoniaConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TelefoniaConfigRepository extends JpaRepository<TelefoniaConfig, UUID> {
}
