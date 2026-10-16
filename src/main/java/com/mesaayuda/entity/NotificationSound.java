package com.mesaayuda.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Sonido de notificación configurado por el SUPER_ADMIN para todos los usuarios.
 * Ámbito URGENCIA: sonido general de urgent/normal/soft. Ámbito TIPO: urgencia
 * y/o audio propio de un tipo de notificación, que manda sobre el general.
 */
@Entity
@Table(name = "notification_sound")
@Getter
@Setter
@NoArgsConstructor
public class NotificationSound {

    public static final String AMBITO_URGENCIA = "URGENCIA";
    public static final String AMBITO_TIPO = "TIPO";
    /** Sonido de una urgencia durante un tema de temporada (clave "NAVIDAD:urgent"). */
    public static final String AMBITO_TEMPORADA = "TEMPORADA";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_sound", updatable = false)
    private UUID idSound;

    @Column(name = "scope_sound", nullable = false, length = 10)
    private String scopeSound;

    @Column(name = "key_sound", nullable = false, length = 60)
    private String keySound;

    /** Solo en TIPO: urgencia elegida para el tipo (null = la de siempre). */
    @Column(name = "urgency_sound", length = 10)
    private String urgencySound;

    /** Audio propio (MP3, WAV u OGG, máximo 1 MB). Se descarga aparte. */
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "audio_sound")
    private byte[] audioSound;

    @Column(name = "mime_sound", length = 40)
    private String mimeSound;

    @Column(name = "file_name_sound", length = 200)
    private String fileNameSound;

    @Column(name = "updated_by_sound")
    private UUID updatedBySound;

    @Column(name = "created_at_sound", nullable = false, updatable = false)
    private OffsetDateTime createdAtSound;

    @Column(name = "updated_at_sound", nullable = false)
    private OffsetDateTime updatedAtSound;
}
