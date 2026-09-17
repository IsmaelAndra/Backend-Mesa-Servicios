package com.mesaayuda.repository;

import com.mesaayuda.entity.SmtpConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SmtpConfigRepository extends JpaRepository<SmtpConfig, Long> {

    Optional<SmtpConfig> findFirstByOrderByIdSmtpAsc();
}