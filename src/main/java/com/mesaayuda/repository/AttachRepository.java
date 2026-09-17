package com.mesaayuda.repository;

import com.mesaayuda.entity.Attach;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AttachRepository extends JpaRepository<Attach, Long> {

    List<Attach> findByTicketAttachIdTicketAndDeletedAttachFalse(Long ticketId);

    boolean existsByTicketAttachIdTicketAndFileNameAttachAndDeletedAttachFalse(Long ticketId, String fileName);

    List<Attach> findByTicketAttachIdTicketAndAnswerAttachIdAnswerAndDeletedAttachFalse(Long ticketId, Long answerId);
}