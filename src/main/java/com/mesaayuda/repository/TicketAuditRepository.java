package com.mesaayuda.repository;

import com.mesaayuda.entity.TicketAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TicketAuditRepository extends JpaRepository<TicketAudit, Long> {

    List<TicketAudit> findByTicketIdTicketOrderByCreatedAtAuditDesc(Long ticketId);
}