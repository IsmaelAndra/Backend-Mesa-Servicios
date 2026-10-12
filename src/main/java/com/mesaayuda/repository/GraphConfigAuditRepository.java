package com.mesaayuda.repository;

import java.util.UUID;

import com.mesaayuda.entity.GraphConfigAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GraphConfigAuditRepository extends JpaRepository<GraphConfigAudit, UUID> {

    List<GraphConfigAudit> findTop200ByOrderByCreatedAtDesc();
}
