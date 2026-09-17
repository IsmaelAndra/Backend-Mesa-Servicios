package com.mesaayuda.repository;

import com.mesaayuda.entity.TicketType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TicketTypeRepository extends JpaRepository<TicketType, Long> {

    List<TicketType> findByDeletedTicketTypeFalseOrderByNameTicketTypeAsc();

    Optional<TicketType> findTopByDeletedTicketTypeFalseOrderByIdTicketTypeAsc();

    boolean existsByNameTicketType(String nameTicketType);
}