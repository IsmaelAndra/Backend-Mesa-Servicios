package com.mesaayuda.entity;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * Estado del doble factor de un usuario.
 *
 * <p>La distinción que más importa aquí es tener un secreto guardado NO es lo
 * mismo que estar enrolado. El secreto se genera la primera vez que alguien
 * entra con el MFA activo, pero pasa a {@code secretAlmacenado} solo cuando el
 * usuario teclea un código válido.
 *
 * <p>Si se guardara directamente, un alta de enrolment abandonada a medias dejaría
 * un secreto en la base del que el usuario ya no tiene copia: en su app hay una
 * entrada que nunca funcionó y aquí habría una que sí. Eso obliga a tener un
 * proceso de recuperación para algo que se puede evitar por completo.
 */
@Entity
@Table(name = "user_mfa")
public class UserMfa {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_user_mfa")
    private UUID idUserMfa;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /** Secreto ya confirmado, cifrado. Null mientras no haya terminado el alta. */
    @Column(name = "secret_almacenado", columnDefinition = "TEXT")
    private String secretAlmacenado;

    /** Secreto propuesto en el alta en curso, cifrado. */
    @Column(name = "secret_pendiente", columnDefinition = "TEXT")
    private String secretPendiente;

    @Column(name = "enrolled", nullable = false)
    private boolean enrolled = false;

    /**
     * Si el usuario puede apagar su propio doble factor.
     *
     * <p>Existe porque en algunos organizaciones el doble factor es obrigatório y
     * quitarse a uno mismo sin que nadie lo sepa es un agujero. Con FALSE, solo el
     * super administrador puede.
     */
    @Column(name = "desactivable_usuario", nullable = false)
    private boolean desactivableUsuario = true;

    /** Último paso de TOTP ya usado, para no aceptar dos veces el mismo código. */
    @Column(name = "ultimo_contador")
    private Long ultimoContador;

    @Column(name = "creado_at_user_mfa", nullable = false)
    private OffsetDateTime creadoAtUserMfa;

    @Column(name = "verificado_at_user_mfa")
    private OffsetDateTime verificadoAtUserMfa;

    public UUID getIdUserMfa() {
        return idUserMfa;
    }

    public void setIdUserMfa(UUID idUserMfa) {
        this.idUserMfa = idUserMfa;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getSecretAlmacenado() {
        return secretAlmacenado;
    }

    public void setSecretAlmacenado(String secretAlmacenado) {
        this.secretAlmacenado = secretAlmacenado;
    }

    public String getSecretPendiente() {
        return secretPendiente;
    }

    public void setSecretPendiente(String secretPendiente) {
        this.secretPendiente = secretPendiente;
    }

    public boolean isEnrolled() {
        return enrolled;
    }

    public void setEnrolled(boolean enrolled) {
        this.enrolled = enrolled;
    }

    public boolean isDesactivableUsuario() {
        return desactivableUsuario;
    }

    public void setDesactivableUsuario(boolean desactivableUsuario) {
        this.desactivableUsuario = desactivableUsuario;
    }

    public Long getUltimoContador() {
        return ultimoContador;
    }

    public void setUltimoContador(Long ultimoContador) {
        this.ultimoContador = ultimoContador;
    }

    public OffsetDateTime getCreadoAtUserMfa() {
        return creadoAtUserMfa;
    }

    public void setCreadoAtUserMfa(OffsetDateTime creadoAtUserMfa) {
        this.creadoAtUserMfa = creadoAtUserMfa;
    }

    public OffsetDateTime getVerificadoAtUserMfa() {
        return verificadoAtUserMfa;
    }

    public void setVerificadoAtUserMfa(OffsetDateTime verificadoAtUserMfa) {
        this.verificadoAtUserMfa = verificadoAtUserMfa;
    }
}
