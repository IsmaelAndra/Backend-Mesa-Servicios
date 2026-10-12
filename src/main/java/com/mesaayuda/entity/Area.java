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
@Table(name = "area")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Area {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_area", updatable = false)
    private UUID idArea;
    @Column(name = "name_area", nullable = false, unique = true, length = 100)
    private String nameArea;

    @Column(name = "description_area")
    private String descriptionArea;

    @Column(name = "icon_area", length = 100)
    private String iconArea;

    @Column(name = "color_area", length = 30)
    private String colorArea;

    @Column(name = "priority_level", nullable = false)
    private Integer priorityLevel = 1;

    @Column(name = "status_area", nullable = false)
    private Boolean statusArea = true;

    @CreationTimestamp
    @Column(name = "created_at_area", nullable = false, updatable = false)
    private OffsetDateTime createdAtArea;

    @UpdateTimestamp
    @Column(name = "updated_at_area", nullable = false)
    private OffsetDateTime updatedAtArea;

    @Column(name = "deleted_area", nullable = false)
    private Boolean deletedArea = false;
}