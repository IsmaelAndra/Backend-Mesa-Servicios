package com.mesaayuda.repository;

import java.util.UUID;

import com.mesaayuda.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByUserIdUserOrderByCreatedAtNotificationDesc(UUID userId);

    List<Notification> findByStatusNotificationOrderByCreatedAtNotificationAsc(String status);

    long countByStatusNotificationAndCreatedAtNotificationAfter(
            String status, OffsetDateTime createdAt);

    long countByUserIdUserAndStatusNotification(UUID userId, String status);

    long countByUserIdUserAndReadNotificationFalse(UUID userId);

    boolean existsByUserIdUserAndStatusNotificationNot(UUID userId, String status);

    /**
     * ¿Se ha avisado ya de este tipo a este ticket? Lo consulta el planificador
     * de SLA para no repetir el mismo aviso cada 15 minutos.
     */
    boolean existsByTicketIdTicketAndTypeNotification(UUID ticketId, String typeNotification);

    @Modifying
    @Query("UPDATE Notification n SET n.readNotification = true, n.readAtNotification = CURRENT_TIMESTAMP "
            + "WHERE n.user.idUser = :userId AND n.readNotification = false")
    int markAllAsRead(@Param("userId") UUID userId);

    @Modifying
    @Query("UPDATE Notification n SET n.readNotification = true, n.readAtNotification = CURRENT_TIMESTAMP "
            + "WHERE n.idNotification = :idNotification AND n.user.idUser = :userId")
    int markAsRead(@Param("idNotification") UUID idNotification, @Param("userId") UUID userId);
}
