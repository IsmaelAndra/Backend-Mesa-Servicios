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
@Table(name = "ai_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AiConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_ai_config", updatable = false)
    private Long idAiConfig;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = false;

    @Column(name = "provider", nullable = false, length = 50)
    private String provider = "OPENAI";

    @Column(name = "model", nullable = false, length = 100)
    private String model = "gpt-4o-mini";

    @Column(name = "api_key", length = 500)
    private String apiKey;

    @Column(name = "base_url", length = 500)
    private String baseUrl;

    @Column(name = "updated_by")
    private Long updatedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}