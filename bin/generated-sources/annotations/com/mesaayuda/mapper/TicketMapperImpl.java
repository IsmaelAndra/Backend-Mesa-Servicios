package com.mesaayuda.mapper;

import com.mesaayuda.dto.TicketResponse;
import com.mesaayuda.entity.Area;
import com.mesaayuda.entity.Category;
import com.mesaayuda.entity.Priority;
import com.mesaayuda.entity.Status;
import com.mesaayuda.entity.SupportTeam;
import com.mesaayuda.entity.Ticket;
import com.mesaayuda.entity.TicketType;
import com.mesaayuda.entity.User;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-09-17T12:15:31-0500",
    comments = "version: 1.5.5.Final, compiler: Eclipse JDT (IDE) 3.46.100.v20260826-1225, environment: Java 21.0.12.1 (Eclipse Adoptium)"
)
@Component
public class TicketMapperImpl implements TicketMapper {

    @Override
    public TicketResponse toResponse(Ticket ticket) {
        if ( ticket == null ) {
            return null;
        }

        String user = null;
        String agent = null;
        String team = null;
        String area = null;
        String category = null;
        String type = null;
        String status = null;
        String priority = null;
        Integer priorityLevel = null;
        Long idTicket = null;
        String titleTicket = null;
        String descriptionTicket = null;
        OffsetDateTime closingDateTicket = null;
        OffsetDateTime createdAtTicket = null;
        OffsetDateTime updatedAtTicket = null;

        user = ticketUserTicketCorporateEmailUser( ticket );
        agent = ticketAgentTicketCorporateEmailUser( ticket );
        team = ticketTeamTicketNameTeam( ticket );
        area = ticketAreaTicketNameArea( ticket );
        category = ticketCategoryTicketNameCategory( ticket );
        type = ticketTypeTicketNameTicketType( ticket );
        status = ticketStatusTicketNameStatus( ticket );
        priority = ticketPriorityTicketNamePriority( ticket );
        priorityLevel = ticketPriorityTicketLevelPriority( ticket );
        idTicket = ticket.getIdTicket();
        titleTicket = ticket.getTitleTicket();
        descriptionTicket = ticket.getDescriptionTicket();
        closingDateTicket = ticket.getClosingDateTicket();
        createdAtTicket = ticket.getCreatedAtTicket();
        updatedAtTicket = ticket.getUpdatedAtTicket();

        TicketResponse ticketResponse = new TicketResponse( idTicket, titleTicket, descriptionTicket, user, agent, team, area, category, type, status, priority, priorityLevel, closingDateTicket, createdAtTicket, updatedAtTicket );

        return ticketResponse;
    }

    @Override
    public List<TicketResponse> toResponse(List<Ticket> tickets) {
        if ( tickets == null ) {
            return null;
        }

        List<TicketResponse> list = new ArrayList<TicketResponse>( tickets.size() );
        for ( Ticket ticket : tickets ) {
            list.add( toResponse( ticket ) );
        }

        return list;
    }

    private String ticketUserTicketCorporateEmailUser(Ticket ticket) {
        if ( ticket == null ) {
            return null;
        }
        User userTicket = ticket.getUserTicket();
        if ( userTicket == null ) {
            return null;
        }
        String corporateEmailUser = userTicket.getCorporateEmailUser();
        if ( corporateEmailUser == null ) {
            return null;
        }
        return corporateEmailUser;
    }

    private String ticketAgentTicketCorporateEmailUser(Ticket ticket) {
        if ( ticket == null ) {
            return null;
        }
        User agentTicket = ticket.getAgentTicket();
        if ( agentTicket == null ) {
            return null;
        }
        String corporateEmailUser = agentTicket.getCorporateEmailUser();
        if ( corporateEmailUser == null ) {
            return null;
        }
        return corporateEmailUser;
    }

    private String ticketTeamTicketNameTeam(Ticket ticket) {
        if ( ticket == null ) {
            return null;
        }
        SupportTeam teamTicket = ticket.getTeamTicket();
        if ( teamTicket == null ) {
            return null;
        }
        String nameTeam = teamTicket.getNameTeam();
        if ( nameTeam == null ) {
            return null;
        }
        return nameTeam;
    }

    private String ticketAreaTicketNameArea(Ticket ticket) {
        if ( ticket == null ) {
            return null;
        }
        Area areaTicket = ticket.getAreaTicket();
        if ( areaTicket == null ) {
            return null;
        }
        String nameArea = areaTicket.getNameArea();
        if ( nameArea == null ) {
            return null;
        }
        return nameArea;
    }

    private String ticketCategoryTicketNameCategory(Ticket ticket) {
        if ( ticket == null ) {
            return null;
        }
        Category categoryTicket = ticket.getCategoryTicket();
        if ( categoryTicket == null ) {
            return null;
        }
        String nameCategory = categoryTicket.getNameCategory();
        if ( nameCategory == null ) {
            return null;
        }
        return nameCategory;
    }

    private String ticketTypeTicketNameTicketType(Ticket ticket) {
        if ( ticket == null ) {
            return null;
        }
        TicketType typeTicket = ticket.getTypeTicket();
        if ( typeTicket == null ) {
            return null;
        }
        String nameTicketType = typeTicket.getNameTicketType();
        if ( nameTicketType == null ) {
            return null;
        }
        return nameTicketType;
    }

    private String ticketStatusTicketNameStatus(Ticket ticket) {
        if ( ticket == null ) {
            return null;
        }
        Status statusTicket = ticket.getStatusTicket();
        if ( statusTicket == null ) {
            return null;
        }
        String nameStatus = statusTicket.getNameStatus();
        if ( nameStatus == null ) {
            return null;
        }
        return nameStatus;
    }

    private String ticketPriorityTicketNamePriority(Ticket ticket) {
        if ( ticket == null ) {
            return null;
        }
        Priority priorityTicket = ticket.getPriorityTicket();
        if ( priorityTicket == null ) {
            return null;
        }
        String namePriority = priorityTicket.getNamePriority();
        if ( namePriority == null ) {
            return null;
        }
        return namePriority;
    }

    private Integer ticketPriorityTicketLevelPriority(Ticket ticket) {
        if ( ticket == null ) {
            return null;
        }
        Priority priorityTicket = ticket.getPriorityTicket();
        if ( priorityTicket == null ) {
            return null;
        }
        Integer levelPriority = priorityTicket.getLevelPriority();
        if ( levelPriority == null ) {
            return null;
        }
        return levelPriority;
    }
}
