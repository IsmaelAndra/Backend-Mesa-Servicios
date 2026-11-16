package com.mesaayuda.repository;

import java.util.UUID;

import com.mesaayuda.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, UUID> {

    List<Task> findByAssignedUserIdUserAndDeletedTaskFalseOrderByStartTaskAscCreatedAtTaskDesc(UUID userId);

    List<Task> findByDeletedTaskFalseOrderByStartTaskAscCreatedAtTaskDesc();

    /**
     * Las actividades son personales: las que tiene asignadas la persona y las
     * que ella creó para otros (así sigue lo que encargó). Nadie ve las de todos.
     */
    @Query("SELECT t FROM Task t WHERE t.deletedTask = false "
            + "AND (t.assignedUser.idUser = :userId OR t.createdBy.idUser = :userId) "
            + "ORDER BY t.startTask ASC, t.createdAtTask DESC")
    List<Task> findMias(@Param("userId") UUID userId);

    List<Task> findByStatusTaskAndDeletedTaskFalseOrderByUpdatedAtTaskDesc(String statusTask);

    /** Actividades con aviso aún no enviado y plazo antes de {@code hasta} (el aviso máximo es 7 días). */
    @Query("SELECT t FROM Task t WHERE t.deletedTask = false AND t.recordatorioMinutos IS NOT NULL "
            + "AND t.recordatorioEnviado IS NULL AND t.dueTask IS NOT NULL AND t.dueTask <= :hasta "
            + "AND t.statusTask <> 'HECHO'")
    List<Task> conRecordatorioPendiente(@Param("hasta") OffsetDateTime hasta);

    List<Task> findByAssignedUserIdUserAndStartTaskBetweenAndDeletedTaskFalse(
            UUID userId, OffsetDateTime start, OffsetDateTime end);

    List<Task> findByDeletedTaskFalseAndUpdatedAtTaskBetween(
            OffsetDateTime start, OffsetDateTime end);
}