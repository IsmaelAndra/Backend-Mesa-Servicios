package com.mesaayuda.controller;

import com.mesaayuda.entity.Notification;
import com.mesaayuda.repository.NotificationRepository;
import com.mesaayuda.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/notifications")
@Tag(name = "Notificaciones", description = "Notificaciones del usuario autenticado")
public class NotificationController {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserService userService;

    @GetMapping
    @Transactional(readOnly = true)
    public List<NotificationResponse> myNotifications() {
        Long userId = userService.getCurrentUser().getIdUser();
        return notificationRepository
                .findByUserIdUserOrderByCreatedAtNotificationDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(
                n.getIdNotification(),
                n.getTicket() != null ? n.getTicket().getIdTicket() : null,
                n.getUser() != null ? n.getUser().getIdUser() : null,
                n.getTypeNotification(),
                n.getSubjectNotification(),
                n.getMessageNotification(),
                n.getStatusNotification(),
                n.getSentAt(),
                n.getCreatedAtNotification());
    }

    public record NotificationResponse(
            Long idNotification,
            Long ticketId,
            Long userId,
            String typeNotification,
            String subjectNotification,
            String messageNotification,
            String statusNotification,
            OffsetDateTime sentAt,
            OffsetDateTime createdAtNotification) {
    }
}