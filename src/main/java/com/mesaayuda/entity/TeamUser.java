package com.mesaayuda.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "team_user", uniqueConstraints =
        @UniqueConstraint(name = "uq_team_user", columnNames = {"team_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TeamUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_team_user", updatable = false)
    private Long idTeamUser;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "team_id", nullable = false)
    private SupportTeam team;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "active_team_user", nullable = false)
    private Boolean activeTeamUser = true;

    @Column(name = "last_assigned_at")
    private OffsetDateTime lastAssignedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}