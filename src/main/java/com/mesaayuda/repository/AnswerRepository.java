package com.mesaayuda.repository;

import com.mesaayuda.entity.Answer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnswerRepository extends JpaRepository<Answer, Long> {

    List<Answer> findByTicketAnswerIdTicketAndDeletedAnswerFalseOrderByCreatedAtAnswerAsc(Long ticketId);

    long countByTicketAnswerIdTicketAndUserAnswerIdUser(Long ticketId, Long userId);
}