package com.mesaayuda.repository;

import com.mesaayuda.entity.GraphConfigAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GraphConfigAuditRepository extends JpaRepository<GraphConfigAudit, Long> {

    List<GraphConfigAudit> findTop200ByOrderByCreatedAtDesc();
}
