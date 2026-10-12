package com.mesaayuda.entity;
import java.util.UUID;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "priority")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Priority {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_priority", updatable = false)
    private UUID idPriority;
    @Column(name = "name_priority", nullable = false, unique = true, length = 100)
    private String namePriority;

    @Column(name = "level_priority", nullable = false, unique = true)
    private Integer levelPriority;

    @Column(name = "description_priority")
    private String descriptionPriority;

    @Column(name = "icon_priority", length = 100)
    private String iconPriority;

    @Column(name = "color_priority", length = 30)
    private String colorPriority;

    @CreationTimestamp
    @Column(name = "created_at_priority", nullable = false, updatable = false)
    private OffsetDateTime createdAtPriority;

    @UpdateTimestamp
    @Column(name = "updated_at_priority", nullable = false)
    private OffsetDateTime updatedAtPriority;

    @Column(name = "deleted_priority", nullable = false)
    private Boolean deletedPriority = false;
}