package com.mesaayuda.repository;

import java.util.UUID;

import com.mesaayuda.entity.TicketAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TicketAuditRepository extends JpaRepository<TicketAudit, UUID> {

    List<TicketAudit> findByTicketIdTicketOrderByCreatedAtAuditDesc(UUID ticketId);
}