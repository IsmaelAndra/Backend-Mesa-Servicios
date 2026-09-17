package com.mesaayuda.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "task", indexes = {
        @Index(name = "idx_task_assigned", columnList = "assigned_user"),
        @Index(name = "idx_task_status", columnList = "status_task"),
        @Index(name = "idx_task_start", columnList = "start_task")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Task {

    public static final String STATUS_PENDIENTE = "PENDIENTE";
    public static final String STATUS_EN_CURSO = "EN_CURSO";
    public static final String STATUS_EN_REVISION = "EN_REVISION";
    public static final String STATUS_HECHO = "HECHO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_task", updatable = false)
    private Long idTask;

    @Column(name = "title_task", nullable = false, length = 255)
    private String titleTask;

    @Column(name = "description_task", columnDefinition = "text")
    private String descriptionTask;

    @Column(name = "status_task", nullable = false, length = 30)
    private String statusTask = STATUS_PENDIENTE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_user", nullable = false)
    private User assignedUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "start_task")
    private OffsetDateTime startTask;

    @Column(name = "due_task")
    private OffsetDateTime dueTask;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "color_task", length = 30)
    private String colorTask = "#062e86";

    @CreationTimestamp
    @Column(name = "created_at_task", nullable = false, updatable = false)
    private OffsetDateTime createdAtTask;

    @UpdateTimestamp
    @Column(name = "updated_at_task", nullable = false)
    private OffsetDateTime updatedAtTask;

    @Column(name = "deleted_task", nullable = false)
    private Boolean deletedTask = false;
}