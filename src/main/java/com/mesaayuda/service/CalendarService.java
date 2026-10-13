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
        // Se listan todas (sin filtrar por fecha en BD): muchas tareas no tienen
        // fecha de inicio y con un BETWEEN directo desaparecían del calendario.
        List<Task> tasks = isAdmin
                ? taskRepository.findByDeletedTaskFalseOrderByStartTaskAscCreatedAtTaskDesc()
                : taskRepository.findByAssignedUserIdUserAndDeletedTaskFalseOrderByStartTaskAscCreatedAtTaskDesc(
                        current.getIdUser());
        for (Task t : tasks) {
            boolean allDay = t.getStartTask() == null && t.getDueTask() == null;
            OffsetDateTime evStart;
            OffsetDateTime evEnd;
            if (allDay) {
                // Una tarea sin fechas ocupa el día entero. Antes se usaba la
                // hora de creación como inicio y creación + 2 h como fin, con lo
                // que el evento salía de 00:00 a 02:00 en vez de cubrir el día.
                evStart = inicioDelDia(t.getCreatedAtTask());
                evEnd = finDelDia(t.getCreatedAtTask());
            } else {
                evStart = t.getStartTask();
                evEnd = endForDates(t.getStartTask(), t.getDueTask(), t.getCreatedAtTask());
            }
            // Todas, también las de día completo, solo si caen en el rango. Antes
            // las tareas sin fechas se devolvían siempre: fuera de su día no se
            // pintaban, pero la leyenda y el contador las sumaban ("4 eventos"
            // en una semana vacía).
            if (evEnd.isBefore(start) || evStart.isAfter(end)) {
                continue;
            }
            result.add(new CalendarEventResponse(
                    "TASK-" + t.getIdTask(),
                    "TASK",
                    t.getTitleTask(),
                    evStart,
                    evEnd,
                    t.getStatusTask(),
                    t.getColorTask(),
                    t.getIdTask(),
                    null,
                    allDay,
                    t.getCreatedAtTask()));
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
            // Un ticket con el SLA ya vencido (creado hace dias con politica
            // corta) devuelve un fin anterior al inicio, y el calendario lo
            // pinta como una barra de ancho cero: se veia "19:34 -> 19:34". Si
            // el plazo ya paso, se muestra una jornada de trabajo desde la
            // creacion, que es lo que el usuario quiere ver.
            if (!evEnd.isAfter(evStart)) {
                evEnd = evStart.plusDays(1);
            }
            if (evEnd.isBefore(start) || evStart.isAfter(end)) continue;
            result.add(new CalendarEventResponse(
                    "TICKET-" + tk.getIdTicket(),
                    "TICKET",
                    "#" + tk.getNumberTicket() + " " + tk.getTitleTicket(),
                    evStart,
                    evEnd,
                    tk.getStatusTicket().getNameStatus(),
                    tk.getPriorityTicket().getColorPriority(),
                    tk.getIdTicket(),
                    tk.getNumberTicket(),
                    false,
                    tk.getCreatedAtTicket()));
        }

        return result;
    }

    private OffsetDateTime endForDates(OffsetDateTime start, OffsetDateTime due, OffsetDateTime created) {
        if (due != null) return due;
        if (start != null) return start;
        return created.plusHours(2);
    }

    /**
     * 00:00:00 del día indicado, en hora de Ecuador. La fecha llega en UTC: sin
     * pasarla a la zona local, las 00:00Z son las 19:00 del día anterior y la
     * barra de "todo el día" ocupaba dos días en el calendario.
     */
    private OffsetDateTime inicioDelDia(OffsetDateTime momento) {
        OffsetDateTime base = momento != null ? momento : OffsetDateTime.now();
        return base.atZoneSameInstant(ZONA).toLocalDate().atStartOfDay(ZONA).toOffsetDateTime();
    }

    /** 23:59:59 del día indicado, en hora de Ecuador. */
    private OffsetDateTime finDelDia(OffsetDateTime momento) {
        return inicioDelDia(momento).plusDays(1).minusSeconds(1);
    }

    private static final java.time.ZoneId ZONA = java.time.ZoneId.of("America/Guayaquil");
}