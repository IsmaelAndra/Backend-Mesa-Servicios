package com.mesaayuda.repository;

import java.util.UUID;

import com.mesaayuda.entity.LoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, UUID> {
    Optional<LoginAttempt> findByUserKey(String userKey);
}