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
@Table(name = "category")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_category", updatable = false)
    private UUID idCategory;
    @Column(name = "name_category", nullable = false, unique = true, length = 100)
    private String nameCategory;

    @Column(name = "description_category")
    private String descriptionCategory;

    @Column(name = "icon_category", length = 100)
    private String iconCategory;

    @Column(name = "color_category", length = 30)
    private String colorCategory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_category")
    private SupportTeam teamCategory;

    /**
     * Área a la que pertenece (V28). En el JSON solo va su id: la relación es
     * perezosa y Jackson no sabe serializar el proxy de Hibernate.
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "area_category")
    private Area areaCategory;

    @com.fasterxml.jackson.annotation.JsonProperty("areaId")
    public UUID getAreaId() {
        return areaCategory == null ? null : areaCategory.getIdArea();
    }

    @CreationTimestamp
    @Column(name = "created_at_category", nullable = false, updatable = false)
    private OffsetDateTime createdAtCategory;

    @UpdateTimestamp
    @Column(name = "updated_at_category", nullable = false)
    private OffsetDateTime updatedAtCategory;

    @Column(name = "deleted_category", nullable = false)
    private Boolean deletedCategory = false;
}