package com.mesaayuda.repository;

import com.mesaayuda.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserIdUserOrderByCreatedAtNotificationDesc(Long userId);

    List<Notification> findByStatusNotificationOrderByCreatedAtNotificationAsc(String status);

    long countByStatusNotificationAndCreatedAtNotificationAfter(
            String status, OffsetDateTime createdAt);
}