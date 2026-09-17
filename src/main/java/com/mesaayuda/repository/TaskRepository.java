package com.mesaayuda.repository;

import com.mesaayuda.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByAssignedUserIdUserAndDeletedTaskFalseOrderByStartTaskAscCreatedAtTaskDesc(Long userId);

    List<Task> findByDeletedTaskFalseOrderByStartTaskAscCreatedAtTaskDesc();

    List<Task> findByStatusTaskAndDeletedTaskFalseOrderByUpdatedAtTaskDesc(String statusTask);

    List<Task> findByAssignedUserIdUserAndStartTaskBetweenAndDeletedTaskFalse(
            Long userId, OffsetDateTime start, OffsetDateTime end);

    List<Task> findByDeletedTaskFalseAndUpdatedAtTaskBetween(
            OffsetDateTime start, OffsetDateTime end);
}