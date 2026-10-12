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
@Table(name = "role")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_role", updatable = false)
    private UUID idRole;
    @Column(name = "name_role", nullable = false, unique = true, length = 100)
    private String nameRole;

    @Column(name = "description_role")
    private String descriptionRole;

    @CreationTimestamp
    @Column(name = "created_at_role", nullable = false, updatable = false)
    private OffsetDateTime createdAtRole;

    @UpdateTimestamp
    @Column(name = "updated_at_role", nullable = false)
    private OffsetDateTime updatedAtRole;

    @Column(name = "deleted_role", nullable = false)
    private Boolean deletedRole = false;
}