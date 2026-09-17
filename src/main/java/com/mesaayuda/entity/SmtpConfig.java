package com.mesaayuda.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "smtp_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SmtpConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_smtp", updatable = false)
    private Long idSmtp;

    @Column(name = "host_smtp", nullable = false)
    private String hostSmtp;

    @Column(name = "port_smtp", nullable = false)
    private Integer portSmtp = 587;

    @Column(name = "username_smtp")
    private String usernameSmtp;

    @Column(name = "password_smtp")
    private String passwordSmtp;

    @Column(name = "from_address")
    private String fromAddress;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "use_tls", nullable = false)
    private Boolean useTls = true;

    @Column(name = "enabled_smtp", nullable = false)
    private Boolean enabledSmtp = true;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}