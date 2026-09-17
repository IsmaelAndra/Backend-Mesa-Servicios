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
@Table(name = "category")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_category", updatable = false)
    private Long idCategory;

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

    @CreationTimestamp
    @Column(name = "created_at_category", nullable = false, updatable = false)
    private OffsetDateTime createdAtCategory;

    @UpdateTimestamp
    @Column(name = "updated_at_category", nullable = false)
    private OffsetDateTime updatedAtCategory;

    @Column(name = "deleted_category", nullable = false)
    private Boolean deletedCategory = false;
}