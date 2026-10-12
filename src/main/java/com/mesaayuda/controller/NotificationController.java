package com.mesaayuda.controller;

import com.mesaayuda.entity.Notification;
import com.mesaayuda.repository.NotificationRepository;
import com.mesaayuda.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.repository.query.Param;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
        UUID userId = userService.getCurrentUser().getIdUser();
        return notificationRepository
                .findByUserIdUserOrderByCreatedAtNotificationDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /** Número de notificaciones sin leer, para el contador del navbar. */
    @GetMapping("/unread-count")
    @Transactional(readOnly = true)
    public Map<String, Object> unreadCount() {
        UUID userId = userService.getCurrentUser().getIdUser();
        return Map.of("unread", notificationRepository.countByUserIdUserAndReadNotificationFalse(userId));
    }

    /** Marca una notificación concreta como leída */
    @PutMapping("/{idNotification}/read")
    @Transactional
    public ResponseEntity<Map<String, Object>> markAsRead(@PathVariable UUID idNotification) {
        UUID userId = userService.getCurrentUser().getIdUser();
        int updated = notificationRepository.markAsRead(idNotification, userId);
        return ResponseEntity.ok(Map.of(
                "success", updated > 0,
                "message", updated > 0
                        ? "Notificación marcada como leída"
                        : "La notificación no existe o no pertenece al usuario"));
    }

    /** Marca todas las notificaciones del usuario como leídas */
    @PutMapping("/read-all")
    @Transactional
    public ResponseEntity<Map<String, Object>> markAllAsRead() {
        UUID userId = userService.getCurrentUser().getIdUser();
        int updated = notificationRepository.markAllAsRead(userId);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Notificaciones marcadas como leídas",
                "updated", updated));
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(
                n.getIdNotification(),
                n.getTicket() != null ? n.getTicket().getIdTicket() : null,
                n.getTicket() != null ? n.getTicket().getNumberTicket() : null,
                n.getUser() != null ? n.getUser().getIdUser() : null,
                n.getTypeNotification(),
                n.getSubjectNotification(),
                n.getMessageNotification(),
                n.getStatusNotification(),
                Boolean.TRUE.equals(n.getReadNotification()),
                n.getReadAtNotification(),
                n.getSentAt(),
                n.getCreatedAtNotification());
    }

    public record NotificationResponse(
            UUID idNotification,
            UUID ticketId,
            /** Número visible del ticket ("#43"); ticketId es la clave para enlazar. */
            Long ticketNumber,
            UUID userId,
            String typeNotification,
            String subjectNotification,
            String messageNotification,
            String statusNotification,
            Boolean readNotification,
            OffsetDateTime readAtNotification,
            OffsetDateTime sentAt,
            OffsetDateTime createdAtNotification) {
    }
}