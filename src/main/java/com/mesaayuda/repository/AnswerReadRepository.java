package com.mesaayuda.repository;

import java.util.UUID;

import com.mesaayuda.entity.AnswerRead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface AnswerReadRepository extends JpaRepository<AnswerRead, UUID> {

    /** Si una respuesta concreta ya fue abierta por esa persona. */
    boolean existsByAnswerIdAnswerAndUserIdUser(UUID answerId, UUID userId);

    /**
     * Marca de lectura de varias respuestas de una vez. Se pasa la lista de ids
     * porque al abrir un ticket se marcan todas las respuestas visibles, y una
     * consulta por mensaje haria decenas de INSERT.
     */
    @Query("select r from AnswerRead r where r.user.idUser = :userId and r.answer.idAnswer in :answerIds")
    List<AnswerRead> findByUserAndAnswers(@Param("userId") UUID userId,
                                         @Param("answerIds") Collection<UUID> answerIds);

    /** Lectores de una respuesta, para el detalle. */
    @Query("select r from AnswerRead r where r.answer.idAnswer = :answerId order by r.readAt asc")
    List<AnswerRead> findByAnswer(@Param("answerId") UUID answerId);

    /** Lecturas de varias respuestas en una sola consulta. */
    List<AnswerRead> findByAnswerIdAnswerIn(Collection<UUID> answerIds);
}
