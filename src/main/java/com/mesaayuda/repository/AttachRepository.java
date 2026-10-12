package com.mesaayuda.repository;

import java.util.UUID;

import com.mesaayuda.entity.Attach;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AttachRepository extends JpaRepository<Attach, UUID> {

    List<Attach> findByTicketAttachIdTicketAndDeletedAttachFalse(UUID ticketId);

    boolean existsByTicketAttachIdTicketAndFileNameAttachAndDeletedAttachFalse(UUID ticketId, String fileName);

    List<Attach> findByTicketAttachIdTicketAndAnswerAttachIdAnswerAndDeletedAttachFalse(UUID ticketId, UUID answerId);
}