package com.mesaayuda.security;

import org.springframework.security.core.GrantedAuthority;

import java.time.Instant;
import java.util.Collection;

/**
 * UserDetails que arrastra el indicador de cambio de contraseña pendiente.
 * <p>
 * Antes el filtro no tenia forma de saberlo sin volver a consultar la base en
 * cada petición, y como no se comprobaba en ningun sitio, la marca
 * {@code must_change_password} era puramente decorativa: el usuario podía
 * entrar con la contraseña inicial y hacer cualquier cosa con la cuenta.
 */
public class MesaUserDetails extends org.springframework.security.core.userdetails.User {

    private final boolean mustChangePassword;

    /** Corte de tokens de acceso (logout, cambio de contraseña); null si no hay. */
    private final Instant tokensValidAfter;

    public MesaUserDetails(String username,
                           String password,
                           Collection<? extends GrantedAuthority> authorities,
                           boolean mustChangePassword) {
        this(username, password, authorities, mustChangePassword, null);
    }

    public MesaUserDetails(String username,
                           String password,
                           Collection<? extends GrantedAuthority> authorities,
                           boolean mustChangePassword,
                           Instant tokensValidAfter) {
        super(username, password, authorities);
        this.mustChangePassword = mustChangePassword;
        this.tokensValidAfter = tokensValidAfter;
    }

    public Instant getTokensValidAfter() {
        return tokensValidAfter;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }
}
