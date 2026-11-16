package com.mesaayuda.repository;

import com.mesaayuda.entity.TicketAutorizado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TicketAutorizadoRepository extends JpaRepository<TicketAutorizado, UUID> {

    @Query("SELECT ta.ticket.idTicket FROM TicketAutorizado ta WHERE ta.usuario.idUser = :userId")
    List<UUID> ticketsDe(@Param("userId") UUID userId);

    @Query("SELECT ta.usuario FROM TicketAutorizado ta WHERE ta.ticket.idTicket = :ticketId")
    List<com.mesaayuda.entity.User> autorizadosDe(@Param("ticketId") UUID ticketId);

    @Modifying
    @Query("DELETE FROM TicketAutorizado ta WHERE ta.ticket.idTicket = :ticketId")
    void borrarDe(@Param("ticketId") UUID ticketId);
}
