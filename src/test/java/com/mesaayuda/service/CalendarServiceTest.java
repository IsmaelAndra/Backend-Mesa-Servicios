package com.mesaayuda.service;

import com.mesaayuda.dto.CalendarEventResponse;
import com.mesaayuda.entity.Priority;
import com.mesaayuda.entity.Role;
import com.mesaayuda.entity.Status;
import com.mesaayuda.entity.Task;
import com.mesaayuda.entity.Ticket;
import com.mesaayuda.entity.User;
import com.mesaayuda.repository.TaskRepository;
import com.mesaayuda.repository.TicketRepository;
import com.mesaayuda.repository.TicketSlaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;

import static com.mesaayuda.IdsPrueba.id;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** El calendario muestra lo que falta por hacer: lo resuelto o terminado no sale. */
@ExtendWith(MockitoExtension.class)
class CalendarServiceTest {

    @Mock private TaskRepository taskRepository;
    @Mock private TicketRepository ticketRepository;
    @Mock private TicketSlaRepository ticketSlaRepository;
    @Mock private UserService userService;
    @InjectMocks private CalendarService servicio;

    private final OffsetDateTime inicio = OffsetDateTime.now().minusDays(5);
    private final OffsetDateTime fin = OffsetDateTime.now().plusDays(5);
    private User agente;

    @BeforeEach
    void preparar() {
        Role rol = new Role();
        rol.setNameRole("AGENT");
        agente = new User();
        agente.setIdUser(id(1));
        agente.setRolUser(rol);
        when(userService.getCurrentUser()).thenReturn(agente);
    }

    @Test
    void unaTareaHechaNoSaleEnElCalendario() {
        Task hecha = tarea(id(10), Task.STATUS_HECHO);
        Task pendiente = tarea(id(11), Task.STATUS_PENDIENTE);
        when(taskRepository.findByAssignedUserIdUserAndDeletedTaskFalseOrderByStartTaskAscCreatedAtTaskDesc(id(1)))
                .thenReturn(List.of(hecha, pendiente));
        when(ticketRepository.findEnCursoParaCalendario(anyBoolean(), any(), any())).thenReturn(List.of());

        List<CalendarEventResponse> eventos = servicio.events(inicio, fin);

        assertEquals(1, eventos.size());
        assertEquals("TASK-" + id(11), eventos.get(0).id());
    }

    @Test
    void losTicketsSalenDeLaConsultaDeEnCursoYLosPlazosSePidenDeUnaVez() {
        when(taskRepository.findByAssignedUserIdUserAndDeletedTaskFalseOrderByStartTaskAscCreatedAtTaskDesc(id(1)))
                .thenReturn(List.of());
        Ticket ticket = new Ticket();
        ticket.setIdTicket(id(20));
        ticket.setNumberTicket(20L);
        ticket.setTitleTicket("Sin correo");
        ticket.setCreatedAtTicket(OffsetDateTime.now());
        Status estado = new Status();
        estado.setNameStatus("ASIGNADO");
        ticket.setStatusTicket(estado);
        Priority prioridad = new Priority();
        prioridad.setColorPriority("#ff0000");
        ticket.setPriorityTicket(prioridad);
        when(ticketRepository.findEnCursoParaCalendario(eq(true), eq(id(1)), eq(fin))).thenReturn(List.of(ticket));
        when(ticketSlaRepository.findByTicketIdTicketIn(List.of(id(20)))).thenReturn(List.of());

        List<CalendarEventResponse> eventos = servicio.events(inicio, fin);

        assertEquals(1, eventos.size());
        verify(ticketRepository, never()).findByAgentTicketIdUserAndDeletedTicketFalseOrderByCreatedAtTicketDesc(any());
        verify(ticketSlaRepository, never()).findByTicketIdTicket(any());
    }

    private Task tarea(java.util.UUID idTarea, String estado) {
        Task t = new Task();
        t.setIdTask(idTarea);
        t.setStatusTask(estado);
        t.setTitleTask("Tarea");
        t.setCreatedAtTask(OffsetDateTime.now());
        return t;
    }
}
