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
@Table(name = "ai_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AiConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_ai_config", updatable = false)
    private UUID idAiConfig;
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = false;

    @Column(name = "provider", nullable = false, length = 50)
    private String provider = "OPENAI";

    @Column(name = "model", nullable = false, length = 100)
    private String model = "gpt-4o-mini";

    @Column(name = "api_key", length = 500)
    private String apiKey;

    /**
     * Si la clave ya esta cifrada. Solo lo usa la migracion de arranque para no
     * reintentar en cada inicio.
     */
    @Column(name = "api_key_encrypted", nullable = false)
    private Boolean apiKeyEncrypted = false;

    @Column(name = "base_url", length = 500)
    private String baseUrl;

    @Column(name = "provider_icon", length = 500)
    private String providerIcon;

    /**
     * Reglas y comportamiento esperado del asistente, escritas por la
     * institucion: tono, limites, pasos obligatorios y que no debe contestar.
     * Se anteponen a las instrucciones de cada accion.
     */
    @Column(name = "rules_text", columnDefinition = "text")
    private String rulesText;

    /** Acciones habilitadas, separadas por comas: SUMMARY, SUGGEST, CHAT. */
    @Column(name = "enabled_actions", nullable = false, length = 200)
    private String enabledActions = "SUMMARY,ANSWER";

    /**
     * Máximo de consultas a la IA por día (todas las acciones juntas). Pensado
     * para la capa gratuita de GroqCloud. Null o 0 = sin límite.
     */
    @Column(name = "daily_limit")
    private Integer dailyLimit = 50;

    @Column(name = "updated_by")
    private UUID updatedBy;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}