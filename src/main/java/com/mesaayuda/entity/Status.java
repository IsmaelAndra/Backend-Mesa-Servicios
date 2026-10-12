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
@Table(name = "status")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Status {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_status", updatable = false)
    private UUID idStatus;
    @Column(name = "name_status", nullable = false, unique = true, length = 100)
    private String nameStatus;

    @Column(name = "description_status")
    private String descriptionStatus;

    @Column(name = "icon_status", length = 100)
    private String iconStatus;

    @Column(name = "color_status", length = 30)
    private String colorStatus;

    @CreationTimestamp
    @Column(name = "created_at_status", nullable = false, updatable = false)
    private OffsetDateTime createdAtStatus;

    @UpdateTimestamp
    @Column(name = "updated_at_status", nullable = false)
    private OffsetDateTime updatedAtStatus;

    @Column(name = "deleted_status", nullable = false)
    private Boolean deletedStatus = false;
}