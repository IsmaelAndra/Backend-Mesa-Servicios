package com.mesaayuda.service;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Pausa del envío cuando Microsoft Graph pide esperar (HTTP 429 o 503 con
 * Retry-After). Mientras dure no se manda nada: insistir solo alarga el castigo.
 */
@Component
public class MailOutboxPausa {

    private final AtomicReference<Instant> hasta = new AtomicReference<>(Instant.EPOCH);

    public void pausar(long segundos) {
        hasta.set(Instant.now().plusSeconds(Math.max(5, Math.min(segundos, 3600))));
    }

    public boolean enPausa() {
        return Instant.now().isBefore(hasta.get());
    }

    /** Hasta cuándo, o null si no hay pausa. */
    public Instant hasta() {
        Instant h = hasta.get();
        return Instant.now().isBefore(h) ? h : null;
    }
}
