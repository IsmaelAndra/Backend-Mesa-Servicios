package com.mesaayuda.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.security.Key;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class JwtTokenProvider {

    /** Identificador del emisor. Se exige tambien al validar el token. */
    private static final String EMISOR = "mesa-ayuda-coac";

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    private Key signingKey;

    @PostConstruct
    public void init() {
        byte[] keyBytes = Base64.getDecoder().decode(secret);
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(Authentication authentication) {
        return generateToken(authentication, expirationMs);
    }

    /**
     * Genera el token con la vida que se le pase. La duracion ya no sale fija del
     * yml: la lee SessionConfigService para que un administrador la cambie desde
     * la pantalla de seguridad sin reiniciar.
     */
    public String generateToken(Authentication authentication, long ttlMs) {
        UserDetails user = (UserDetails) authentication.getPrincipal();
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + ttlMs);
        String authorities = user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));

        return Jwts.builder()
                .setSubject(user.getUsername())
                .claim("roles", authorities)
                // Identificador unico por token. Sirve para dos cosas: poder
                // invalidar un token concreto en una lista de revocacion, y
                // reconocer cuando un refresh token se reutiliza.
                .setId(UUID.randomUUID().toString())
                .setIssuer(EMISOR)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(signingKey, SignatureAlgorithm.HS512)
                .compact();
    }

    public String getUsernameFromToken(String token) {
        return getClaims(token).getSubject();
    }

    /** Instante de expiracion del token, o null si el token no es valido. */
    public Date getExpirationFromToken(String token) {
        try {
            return getClaims(token).getExpiration();
        } catch (Exception e) {
            return null;
        }
    }

    /** Instante de emision del token, o null si el token no es valido. */
    public Date getIssuedAtFromToken(String token) {
        try {
            return getClaims(token).getIssuedAt();
        } catch (Exception e) {
            return null;
        }
    }

    /** Segundos que le quedan al token; 0 si ya caduco o no se puede leer. */
    public long segundosRestantes(String token) {
        Date exp = getExpirationFromToken(token);
        if (exp == null) {
            return 0L;
        }
        long restante = (exp.getTime() - System.currentTimeMillis()) / 1000L;
        return Math.max(0L, restante);
    }

    public boolean validateToken(String token) {
        try {
            getClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Claims getClaims(String token) {
        return Jwts.parserBuilder()
                // El token se emite con issuer, así que exiguirlo también al
                // leerlo. Sin esta comprobación, un JWT firmado con la MISMA
                // clave pero emitido para otra aplicación se aceptaría como si
                // fuera nuestro: la firma dejaría de ser la frontera.
                .requireIssuer(EMISOR)
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}