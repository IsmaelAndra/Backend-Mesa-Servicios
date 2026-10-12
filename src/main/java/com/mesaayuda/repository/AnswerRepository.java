package com.mesaayuda.repository;

import java.util.UUID;

import com.mesaayuda.entity.Answer;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnswerRepository extends JpaRepository<Answer, UUID> {

    List<Answer> findByTicketAnswerIdTicketAndDeletedAnswerFalseOrderByCreatedAtAnswerAsc(UUID ticketId);

    long countByTicketAnswerIdTicketAndUserAnswerIdUser(UUID ticketId, UUID userId);

    /**
     * Paginado del hilo, del mas reciente al mas antiguo.
     * <p>
     * El chat carga primero la ultima pagina y, al subir el scroll, pide la
     * anterior, asi que el orden de la consulta va al reves del orden en que se
     * dibuja. El servicio invierte cada pagina antes de devolverla.
     */
    List<Answer> findByTicketAnswerIdTicketAndDeletedAnswerFalseOrderByCreatedAtAnswerDescIdAnswerDesc(
            UUID ticketId, Pageable pageable);

    /** Igual, pero ocultando las notas internas para quien no es personal. */
    List<Answer> findByTicketAnswerIdTicketAndInternalAnswerFalseAndDeletedAnswerFalseOrderByCreatedAtAnswerDescIdAnswerDesc(
            UUID ticketId, Pageable pageable);

    long countByTicketAnswerIdTicketAndDeletedAnswerFalse(UUID ticketId);

    long countByTicketAnswerIdTicketAndInternalAnswerFalseAndDeletedAnswerFalse(UUID ticketId);
}