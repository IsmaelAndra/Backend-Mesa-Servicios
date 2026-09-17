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
@Table(name = "notification_template")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NotificationTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_template", updatable = false)
    private Long idTemplate;

    @Column(name = "name_template", nullable = false, unique = true, length = 100)
    private String nameTemplate;

    @Column(name = "type_template", nullable = false, length = 50)
    private String typeTemplate;

    @Column(name = "subject_template", nullable = false, length = 255)
    private String subjectTemplate;

    @Column(name = "body_template", nullable = false, columnDefinition = "text")
    private String bodyTemplate;

    @Column(name = "active_template", nullable = false)
    private Boolean activeTemplate = true;

    @CreationTimestamp
    @Column(name = "created_at_template", nullable = false, updatable = false)
    private OffsetDateTime createdAtTemplate;

    @UpdateTimestamp
    @Column(name = "updated_at_template", nullable = false)
    private OffsetDateTime updatedAtTemplate;
}