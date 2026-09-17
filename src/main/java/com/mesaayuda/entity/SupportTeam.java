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
@Table(name = "support_team")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SupportTeam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_team", updatable = false)
    private Long idTeam;

    @Column(name = "name_team", nullable = false, unique = true, length = 100)
    private String nameTeam;

    @Column(name = "description_team")
    private String descriptionTeam;

    @Column(name = "assignment_method", nullable = false, length = 30)
    private String assignmentMethod = "ROUND_ROBIN";

    @Column(name = "active_team", nullable = false)
    private Boolean activeTeam = true;

    @CreationTimestamp
    @Column(name = "created_at_team", nullable = false, updatable = false)
    private OffsetDateTime createdAtTeam;

    @UpdateTimestamp
    @Column(name = "updated_at_team", nullable = false)
    private OffsetDateTime updatedAtTeam;
}