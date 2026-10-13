package com.mesaayuda.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "asset_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AssetHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_asset_history", updatable = false)
    private UUID idAssetHistory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    /** Persona a la que afecta el movimiento (ver V12). Puede ser null. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    /** En una reasignación, quién tenía el activo antes. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_user_id")
    private User previousUser;

    /** Quién hizo el cambio. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performed_by_id")
    private User performedBy;

    @Column(name = "action_type", nullable = false, length = 30)
    private String actionType;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at_asset_history", nullable = false, updatable = false)
    private OffsetDateTime createdAtAssetHistory;
}