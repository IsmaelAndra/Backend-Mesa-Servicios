package com.mesaayuda.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Configuración de la telefonía (V14, una sola fila: {@link IdsConfiguracion#TELEFONIA}).
 * Lo que la edita el SUPER_ADMIN y lo que informa el puente al conectarse.
 */
@Entity
@Table(name = "telefonia_config")
@Getter
@Setter
@NoArgsConstructor
public class TelefoniaConfig {

    @Id
    @Column(name = "id_telefonia_config")
    private UUID idTelefoniaConfig;

    @Column(name = "enabled_telefonia", nullable = false)
    private Boolean enabledTelefonia = false;

    @Column(name = "ucm_url", length = 300)
    private String ucmUrl;

    @Column(name = "ucm_usuario", length = 120)
    private String ucmUsuario;

    /** Cifrada con SecretCryptoService; nunca sale en las respuestas de Administración. */
    @Column(name = "ucm_clave_cifrada", columnDefinition = "text")
    private String ucmClaveCifrada;

    /** SHA-256 (hex) del token del puente. */
    @Column(name = "token_hash", length = 64)
    private String tokenHash;

    @Column(name = "token_creado_at")
    private OffsetDateTime tokenCreadoAt;

    @Column(name = "puente_visto_at")
    private OffsetDateTime puenteVistoAt;

    @Column(name = "puente_version", length = 40)
    private String puenteVersion;

    @Column(name = "puente_ip", length = 64)
    private String puenteIp;

    @Column(name = "puente_ucm_ok")
    private Boolean puenteUcmOk;

    @Column(name = "puente_ucm_error", length = 500)
    private String puenteUcmError;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "updated_at_telefonia", nullable = false)
    private OffsetDateTime updatedAtTelefonia;
}
