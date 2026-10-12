package com.mesaayuda.repository;

import java.util.UUID;

import com.mesaayuda.entity.TicketType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TicketTypeRepository extends JpaRepository<TicketType, UUID> {

    List<TicketType> findByDeletedTicketTypeFalseOrderByNameTicketTypeAsc();

    Optional<TicketType> findTopByDeletedTicketTypeFalseOrderByCreatedAtTicketTypeAsc();

    boolean existsByNameTicketType(String nameTicketType);
}