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
@Table(name = "report_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReportConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_report", updatable = false)
    private UUID idReport;
    @Column(name = "name_report", nullable = false, unique = true, length = 100)
    private String nameReport;

    @Column(name = "title_report", nullable = false, length = 255)
    private String titleReport;

    @Column(name = "description_report", length = 500)
    private String descriptionReport;

    @Column(name = "query_sql", nullable = false, columnDefinition = "TEXT")
    private String querySql;

    @Column(name = "active_report", nullable = false)
    private Boolean activeReport = true;

    @Column(name = "icon_report", length = 100)
    private String iconReport;

    @Column(name = "color_report", length = 30)
    private String colorReport;

    @CreationTimestamp
    @Column(name = "created_at_report", nullable = false, updatable = false)
    private OffsetDateTime createdAtReport;

    @UpdateTimestamp
    @Column(name = "updated_at_report", nullable = false)
    private OffsetDateTime updatedAtReport;

    @Column(name = "deleted_report", nullable = false)
    private Boolean deletedReport = false;
}