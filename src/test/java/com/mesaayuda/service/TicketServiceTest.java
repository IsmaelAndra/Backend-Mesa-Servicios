package com.mesaayuda.service;

import com.mesaayuda.entity.Ticket;
import com.mesaayuda.entity.User;
import com.mesaayuda.mapper.AnswerMapper;
import com.mesaayuda.mapper.TicketMapper;
import com.mesaayuda.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock private TicketRepository ticketRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private StatusRepository statusRepository;
    @Mock private PriorityRepository priorityRepository;
    @Mock private AnswerRepository answerRepository;
    @Mock private AttachRepository attachRepository;
    @Mock private TicketTypeRepository ticketTypeRepository;
    @Mock private UserRepository userRepository;
    @Mock private RoutingService routingService;
    @Mock private SlaService slaService;
    @Mock private NotificationService notificationService;
    @Mock private AuditService auditService;
    @Mock private UserService userService;
    @Mock private TicketMapper ticketMapper;
    @Mock private AnswerMapper answerMapper;

    @InjectMocks private TicketService ticketService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(ticketService, "uploadDir", "target/test-uploads");
    }

    private User user(Long id) {
        User u = new User();
        u.setIdUser(id);
        return u;
    }

    @Test
    void myTickets_queriesOnlyCurrentRequester() {
        User current = user(10L);
        when(userService.getCurrentUser()).thenReturn(current);

        Ticket t1 = mock(Ticket.class);
        Ticket t2 = mock(Ticket.class);
        when(ticketRepository.findByUserTicketIdUserAndDeletedTicketFalseOrderByCreatedAtTicketDesc(10L))
                .thenReturn(List.of(t1, t2));

        var result = ticketService.myTickets();

        verify(ticketRepository).findByUserTicketIdUserAndDeletedTicketFalseOrderByCreatedAtTicketDesc(10L);
        verify(ticketMapper).toResponse(t1);
        verify(ticketMapper).toResponse(t2);
        assertEquals(2, result.size());
    }

    @Test
    void assignedTickets_queriesOnlyCurrentAgent() {
        User current = user(10L);
        when(userService.getCurrentUser()).thenReturn(current);

        Ticket t = mock(Ticket.class);
        when(ticketRepository.findByAgentTicketIdUserAndDeletedTicketFalseOrderByCreatedAtTicketDesc(10L))
                .thenReturn(List.of(t));

        var result = ticketService.assignedTickets();

        verify(ticketRepository).findByAgentTicketIdUserAndDeletedTicketFalseOrderByCreatedAtTicketDesc(10L);
        verify(ticketMapper).toResponse(t);
        assertEquals(1, result.size());
    }

    @Test
    void search_callsRepositoryWithSpecAndPageable() {
        Ticket t = mock(Ticket.class);
        when(ticketRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(t)));

        var page = ticketService.search(null, null, null, null, null, null, null, 0, 10);

        verify(ticketRepository).findAll(any(Specification.class), any(Pageable.class));
        verify(ticketMapper).toResponse(t);
        assertEquals(1, page.getContent().size());
    }

    @Test
    void uploadAttachment_emptyFile_throws() {
        Ticket ticket = mock(Ticket.class);
        when(ticketRepository.findById(1L)).thenReturn(java.util.Optional.of(ticket));

        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(true);

        com.mesaayuda.exception.BadRequestException ex = org.junit.jupiter.api.Assertions.assertThrows(
                com.mesaayuda.exception.BadRequestException.class,
                () -> ticketService.uploadAttachment(1L, null, file));
        assertEquals("El archivo está vacío", ex.getMessage());
        verify(attachRepository, never()).save(any());
    }
}