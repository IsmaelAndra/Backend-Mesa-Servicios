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

    public TaskResponse create(TaskRequest request) {
        User creator = userService.getCurrentUser();
        User assigned = userRepository.findById(request.assignedUserId())
                .filter(u -> !u.getDeletedUser())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", "id", request.assignedUserId()));

        Task task = new Task();
        task.setTitleTask(request.title().trim());
        task.setDescriptionTask(request.description());
        task.setStatusTask(normalizeStatus(request.status()));
        task.setAssignedUser(assigned);
        task.setCreatedBy(creator);
        task.setStartTask(request.start());
        task.setDueTask(request.due());
        task.setDurationMinutes(request.durationMinutes());
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
        List<Task> tasks;
        if (current.getRolUser().getNameRole().equals("AGENT")) {
            tasks = taskRepository
                    .findByAssignedUserIdUserAndDeletedTaskFalseOrderByStartTaskAscCreatedAtTaskDesc(
                            current.getIdUser());
        } else {
            tasks = taskRepository.findByDeletedTaskFalseOrderByStartTaskAscCreatedAtTaskDesc();
        }
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

        task.setTitleTask(request.title() == null ? task.getTitleTask() : request.title().trim());
        task.setDescriptionTask(request.description() == null ? task.getDescriptionTask() : request.description());
        if (request.status() != null && !request.status().isBlank()) {
            task.setStatusTask(normalizeStatus(request.status()));
        }
        task.setAssignedUser(assigned);
        task.setStartTask(request.start() == null ? task.getStartTask() : request.start());
        task.setDueTask(request.due() == null ? task.getDueTask() : request.due());
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
        boolean isAdmin = current.getRolUser().getNameRole().equals("ADMIN")
                || current.getRolUser().getNameRole().equals("SUPER_ADMIN");
        if (!isAdmin && !task.getAssignedUser().getIdUser().equals(current.getIdUser())) {
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
                task.getUpdatedAtTask());
    }
}