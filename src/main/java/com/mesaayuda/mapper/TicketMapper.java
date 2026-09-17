package com.mesaayuda.mapper;

import com.mesaayuda.dto.TicketResponse;
import com.mesaayuda.entity.Ticket;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper(componentModel = "spring")
public interface TicketMapper {

    TicketMapper INSTANCE = Mappers.getMapper(TicketMapper.class);

    @Mapping(target = "user", source = "userTicket.corporateEmailUser")
    @Mapping(target = "agent", source = "agentTicket.corporateEmailUser")
    @Mapping(target = "team", source = "teamTicket.nameTeam")
    @Mapping(target = "area", source = "areaTicket.nameArea")
    @Mapping(target = "category", source = "categoryTicket.nameCategory")
    @Mapping(target = "type", source = "typeTicket.nameTicketType")
    @Mapping(target = "status", source = "statusTicket.nameStatus")
    @Mapping(target = "priority", source = "priorityTicket.namePriority")
    @Mapping(target = "priorityLevel", source = "priorityTicket.levelPriority")
    TicketResponse toResponse(Ticket ticket);

    List<TicketResponse> toResponse(List<Ticket> tickets);
}