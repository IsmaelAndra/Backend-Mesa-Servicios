package com.mesaayuda.repository;

import java.util.UUID;

import com.mesaayuda.entity.UserMfa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserMfaRepository extends JpaRepository<UserMfa, UUID> {

    Optional<UserMfa> findByUser_IdUser(UUID userId);

    Optional<UserMfa> findByUser_IdUserAndEnrolledTrue(UUID userId);

    /** Cuántas cuentas hay enroladas, para mostrarlo en la pantalla de ajuste. */
    long countByEnrolledTrue();
}
