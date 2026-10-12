package com.mesaayuda.repository;

import java.util.UUID;

import com.mesaayuda.entity.ChatConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ChatConfigRepository extends JpaRepository<ChatConfig, UUID> {

    /** La tabla es un singleton: siempre se lee la fila de id 1. */
    Optional<ChatConfig> findTopByOrderByIdChatConfigAsc();
}
