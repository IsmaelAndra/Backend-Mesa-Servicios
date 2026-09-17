package com.mesaayuda.service;

import com.mesaayuda.dto.CalendarEventResponse;
import com.mesaayuda.entity.Task;
import com.mesaayuda.entity.Ticket;
import com.mesaayuda.entity.TicketSla;
import com.mesaayuda.entity.User;
import com.mesaayuda.repository.TaskRepository;
import com.mesaayuda.repository.TicketRepository;
import com.mesaayuda.repository.TicketSlaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class CalendarService {

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TicketSlaRepository ticketSlaRepository;

    @Autowired
    private UserService userService;

    public List<CalendarEventResponse> events(OffsetDateTime start, OffsetDateTime end) {
        User current = userService.getCurrentUser();
        String role = current.getRolUser().getNameRole();
        boolean isAdmin = role.equals("ADMIN") || role.equals("SUPER_ADMIN");

        List<CalendarEventResponse> result = new ArrayList<>();

        // ===== TAREAS =====
        List<Task> tasks = isAdmin
                ? taskRepository.findByDeletedTaskFalseAndUpdatedAtTaskBetween(start, end)
                : taskRepository.findByAssignedUserIdUserAndStartTaskBetweenAndDeletedTaskFalse(
                        current.getIdUser(), start, end);
        for (Task t : tasks) {
            OffsetDateTime evStart = t.getStartTask() != null ? t.getStartTask() : t.getCreatedAtTask();
            OffsetDateTime evEnd = endForDates(t.getStartTask(), t.getDueTask(), t.getCreatedAtTask());
            result.add(new CalendarEventResponse(
                    "TASK-" + t.getIdTask(),
                    "TASK",
                    t.getTitleTask(),
                    evStart,
                    evEnd,
                    t.getStatusTask(),
                    t.getColorTask(),
                    t.getIdTask(),
                    t.getStartTask() == null && t.getDueTask() == null));
        }

        // ===== TICKETS ASIGNADOS =====
        List<Ticket> tickets = isAdmin
                ? ticketRepository.findByDeletedTicketFalseOrderByCreatedAtTicketDesc()
                : ticketRepository.findByAgentTicketIdUserAndDeletedTicketFalseOrderByCreatedAtTicketDesc(
                        current.getIdUser());
        for (Ticket tk : tickets) {
            TicketSla sla = ticketSlaRepository.findByTicketIdTicket(tk.getIdTicket()).orElse(null);
            OffsetDateTime evStart = tk.getCreatedAtTicket();
            OffsetDateTime evEnd = sla != null && sla.getResolutionDueAt() != null
                    ? sla.getResolutionDueAt()
                    : tk.getCreatedAtTicket().plusDays(1);
            if (evEnd.isBefore(start) || evStart.isAfter(end)) continue;
            result.add(new CalendarEventResponse(
                    "TICKET-" + tk.getIdTicket(),
                    "TICKET",
                    "#" + tk.getIdTicket() + " " + tk.getTitleTicket(),
                    evStart,
                    evEnd,
                    tk.getStatusTicket().getNameStatus(),
                    tk.getPriorityTicket().getColorPriority(),
                    tk.getIdTicket(),
                    false));
        }

        return result;
    }

    private OffsetDateTime endForDates(OffsetDateTime start, OffsetDateTime due, OffsetDateTime created) {
        if (due != null) return due;
        if (start != null) return start;
        return created.plusHours(2);
    }
}