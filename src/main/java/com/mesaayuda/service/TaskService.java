package com.mesaayuda.service;

import com.mesaayuda.dto.TaskRequest;
import com.mesaayuda.dto.TaskResponse;
import com.mesaayuda.entity.Task;
import com.mesaayuda.entity.User;
import com.mesaayuda.exception.BadRequestException;
import com.mesaayuda.exception.ResourceNotFoundException;
import com.mesaayuda.repository.TaskRepository;
import com.mesaayuda.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    private static final List<String> STATUSES = List.of(
            Task.STATUS_PENDIENTE, Task.STATUS_EN_CURSO, Task.STATUS_EN_REVISION, Task.STATUS_HECHO);

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private NotificationService notificationService;

    /**
     * Encargar una actividad a otra persona es cosa del jefe de área o del súper
     * administrador. Un agente o un usuario solo se asigna actividades a sí mismo
     * (al editar puede dejar la persona que ya tenía).
     */
    void exigirPuedeAsignarA(User actor, User asignado, User asignadoActual) {
        if (actor == null || asignado == null || asignado.getIdUser().equals(actor.getIdUser())) {
            return;
        }
        if (asignadoActual != null && asignadoActual.getIdUser().equals(asignado.getIdUser())) {
            return;
        }
        String rol = actor.getRolUser() == null ? "" : actor.getRolUser().getNameRole();
        if (!com.mesaayuda.security.Roles.esSuperAdmin(rol) && !com.mesaayuda.security.Roles.esJefe(rol)) {
            throw new com.mesaayuda.exception.BadRequestException("Solo un jefe de área puede asignar actividades a otra persona");
        }
    }

    public TaskResponse create(TaskRequest request) {
        User creator = userService.getCurrentUser();
        User assigned = userRepository.findById(request.assignedUserId())
                .filter(u -> !u.getDeletedUser())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", "id", request.assignedUserId()));
        exigirPuedeAsignarA(creator, assigned, null);

        Task task = new Task();
        task.setTitleTask(request.title().trim());
        task.setDescriptionTask(request.description());
        task.setStatusTask(normalizeStatus(request.status()));
        task.setAssignedUser(assigned);
        task.setCreatedBy(creator);
        task.setStartTask(request.start());
        task.setDueTask(request.due());
        exigirFechasEnOrden(task);
        task.setDurationMinutes(request.durationMinutes());
        task.setRecordatorioMinutos(recordatorioValido(request.recordatorioMinutos()));
        if (request.color() != null && !request.color().isBlank()) {
            task.setColorTask(request.color());
        }
        task = taskRepository.save(task);

        notificationService.notifyTaskAssigned(task);
        log.info("Tarea creada: '{}' asignada a {}", task.getTitleTask(), assigned.getCorporateEmailUser());
        return toResponse(task);
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> findAll(String status) {
        User current = userService.getCurrentUser();
        // Personales para todos los roles, también el súper admin: las suyas y las que encargó.
        List<Task> tasks = taskRepository.findMias(current.getIdUser());
        if (status != null && !status.isBlank()) {
            tasks = tasks.stream()
                    .filter(t -> t.getStatusTask().equalsIgnoreCase(status))
                    .toList();
        }
        return tasks.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public TaskResponse findById(UUID id) {
        return toResponse(getOwnedOrAdminTask(id));
    }

    public TaskResponse update(UUID id, TaskRequest request) {
        Task task = getOwnedOrAdminTask(id);
        User assigned = userRepository.findById(request.assignedUserId())
                .filter(u -> !u.getDeletedUser())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", "id", request.assignedUserId()));
        exigirPuedeAsignarA(userService.getCurrentUser(), assigned, task.getAssignedUser());

        task.setTitleTask(request.title() == null ? task.getTitleTask() : request.title().trim());
        task.setDescriptionTask(request.description() == null ? task.getDescriptionTask() : request.description());
        if (request.status() != null && !request.status().isBlank()) {
            task.setStatusTask(normalizeStatus(request.status()));
        }
        task.setAssignedUser(assigned);
        task.setStartTask(request.start() == null ? task.getStartTask() : request.start());
        OffsetDateTime plazoAnterior = task.getDueTask();
        Integer avisoAnterior = task.getRecordatorioMinutos();
        task.setDueTask(request.due() == null ? task.getDueTask() : request.due());
        exigirFechasEnOrden(task);
        task.setRecordatorioMinutos(recordatorioValido(request.recordatorioMinutos()));
        // Cambió el plazo o el aviso: el recordatorio vuelve a quedar pendiente.
        if (!java.util.Objects.equals(plazoAnterior, task.getDueTask())
                || !java.util.Objects.equals(avisoAnterior, task.getRecordatorioMinutos())) {
            task.setRecordatorioEnviado(null);
        }
        task.setDurationMinutes(request.durationMinutes() == null ? task.getDurationMinutes() : request.durationMinutes());
        if (request.color() != null && !request.color().isBlank()) {
            task.setColorTask(request.color());
        }
        task = taskRepository.save(task);
        return toResponse(task);
    }

    public TaskResponse changeStatus(UUID id, String status) {
        Task task = getOwnedOrAdminTask(id);
        task.setStatusTask(normalizeStatus(status));
        task = taskRepository.save(task);
        return toResponse(task);
    }

    public void delete(UUID id) {
        Task task = getOwnedOrAdminTask(id);
        task.setDeletedTask(true);
        taskRepository.save(task);
    }

    private Task getOwnedOrAdminTask(UUID id) {
        User current = userService.getCurrentUser();
        Task task = taskRepository.findById(id)
                .filter(t -> !t.getDeletedTask())
                .orElseThrow(() -> new ResourceNotFoundException("Tarea", "id", id));
        // Quien la tiene asignada o quien la encargó; nadie más, ni el súper admin.
        boolean creador = task.getCreatedBy() != null && task.getCreatedBy().getIdUser().equals(current.getIdUser());
        if (!creador && !task.getAssignedUser().getIdUser().equals(current.getIdUser())) {
            throw new AccessDeniedException("No tiene permisos sobre esta tarea");
        }
        return task;
    }

    private String normalizeStatus(String status) {
        String s = (status == null || status.isBlank()) ? Task.STATUS_PENDIENTE : status.toUpperCase();
        if (!STATUSES.contains(s)) {
            throw new BadRequestException("Estado inválido. Use: PENDIENTE, EN_CURSO, EN_REVISION, HECHO");
        }
        return s;
    }

    private TaskResponse toResponse(Task task) {
        return new TaskResponse(
                task.getIdTask(),
                task.getTitleTask(),
                task.getDescriptionTask(),
                task.getStatusTask(),
                task.getAssignedUser().getIdUser(),
                task.getAssignedUser().getCorporateEmailUser(),
                task.getCreatedBy().getIdUser(),
                task.getCreatedBy().getCorporateEmailUser(),
                task.getStartTask(),
                task.getDueTask(),
                task.getDurationMinutes(),
                task.getColorTask(),
                task.getCreatedAtTask(),
                task.getUpdatedAtTask(),
                task.getRecordatorioMinutos());
    }

    /** Entre 5 minutos y 7 días antes; cualquier otro valor = sin aviso. */
    private static Integer recordatorioValido(Integer minutos) {
        return minutos == null || minutos < 5 || minutos > 7 * 24 * 60 ? null : minutos;
    }

    /**
     * Avisa (campana + correo) a quien tiene cada actividad cuyo plazo está a
     * menos de su "recordatorioMinutos". Lo llama RecordatorioActividadesScheduler.
     * Lo terminado no se avisa; cada actividad se avisa una sola vez por plazo.
     */
    public int enviarRecordatoriosPendientes(OffsetDateTime ahora) {
        int enviados = 0;
        for (Task t : taskRepository.conRecordatorioPendiente(ahora.plusDays(7))) {
            if (Task.STATUS_HECHO.equals(t.getStatusTask()) || t.getDueTask() == null
                    || t.getRecordatorioMinutos() == null) {
                continue;
            }
            if (ahora.isBefore(t.getDueTask().minusMinutes(t.getRecordatorioMinutos()))) {
                continue;
            }
            notificationService.notifyTaskReminder(t);
            t.setRecordatorioEnviado(ahora);
            taskRepository.save(t);
            enviados++;
        }
        return enviados;
    }

    /** El fin no puede ir antes del inicio (antes se aceptaba). */
    private static void exigirFechasEnOrden(Task t) {
        if (t.getStartTask() != null && t.getDueTask() != null && t.getDueTask().isBefore(t.getStartTask())) {
            throw new com.mesaayuda.exception.BadRequestException("La fecha de fin no puede ser anterior a la de inicio");
        }
    }
}
