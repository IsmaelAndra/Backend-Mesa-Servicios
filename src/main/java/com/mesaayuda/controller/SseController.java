package com.mesaayuda.controller;

import com.mesaayuda.entity.User;
import com.mesaayuda.repository.UserRepository;
import com.mesaayuda.service.ExpiringStateMap;
import com.mesaayuda.service.SseEventBroker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Canal de eventos en tiempo real.
 *
 * <p>EventSource no permite enviar cabeceras, así que el canal no puede llevar
 * el token en Authorization como el resto de la API. Antes se pasaba el JWT de
 * acceso en la URL ({@code ?token=...}), y una URL acaba en los logs de acceso
 * del proxy, en el historial del navegador y en las herramientas de monitorización:
 * cualquiera que leyera esos registros obtenía un token válido para toda la API.
 *
 * <p>Ahora se hace en dos pasos. El cliente pide un ticket con su sesión normal
 * (POST con Authorization) y abre el canal con ese ticket. El ticket solo sirve
 * para abrir el canal, caduca a los 30 segundos y se consume al usarlo, así que
 * aunque quede escrito en un log ya no vale para nada.
 *
 * <p>Los tickets viven en memoria, igual que las suscripciones de
 * {@link SseEventBroker}: el canal ya está atado a la instancia que lo sirve.
 */
@RestController
@RequestMapping("/events")
public class SseController {

    private static final Duration VIGENCIA_TICKET = Duration.ofSeconds(30);
    private static final int MAX_TICKETS = 10_000;

    private final SecureRandom aleatorio = new SecureRandom();

    /** Ticket de un solo uso -> correo del usuario que lo pidió. */
    private final ExpiringStateMap<String, String> tickets =
            new ExpiringStateMap<>(VIGENCIA_TICKET, MAX_TICKETS);

    @Autowired
    private SseEventBroker sseEventBroker;

    @Autowired
    private UserRepository userRepository;

    /**
     * Emite un ticket para abrir el canal. Exige sesión: lo protege la regla
     * general de SecurityConfig, porque solo GET /events es público.
     */
    @PostMapping("/ticket")
    public ResponseEntity<Map<String, String>> ticket() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null) {
            return ResponseEntity.status(401).build();
        }
        byte[] bytes = new byte[32];
        aleatorio.nextBytes(bytes);
        String ticket = HexFormat.of().formatHex(bytes);
        tickets.put(ticket, auth.getName());
        return ResponseEntity.ok(Map.of("ticket", ticket));
    }

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream(@RequestParam(required = false) String ticket) {
        String email = ticket == null || ticket.isBlank() ? null : tickets.tomar(ticket.trim());
        if (email == null) {
            return ResponseEntity.status(401).build();
        }
        // Una sola consulta: se filtra por los mismos motivos que
        // JwtAuthenticationFilter, porque una cuenta desactivada o con la
        // contraseña inicial sin cambiar no debe abrir el canal de eventos, ya
        // que por ahi seguirian llegando avisos de tickets aunque el resto de la
        // API ya le responda 403.
        User usuario = userRepository.findByCorporateEmailUser(email)
                .filter(u -> !Boolean.TRUE.equals(u.getDeletedUser()))
                .filter(u -> "ACTIVE".equalsIgnoreCase(u.getStatusUser()))
                .filter(u -> !Boolean.TRUE.equals(u.getMustChangePassword()))
                .orElse(null);
        if (usuario == null) {
            return ResponseEntity.status(401).build();
        }
        UUID userId = usuario.getIdUser();
        // El rol se le pasa al canal porque de el depende a quien se envian los
        // eventos de ticket: el personal ve la cola entera y un solicitador solo
        // los suyos. Sin esta distincion, todos recibian todos.
        boolean esPersonal = usuario.getRolUser() != null
                && List.of("AGENT", "ADMIN", "SUPER_ADMIN").contains(usuario.getRolUser().getNameRole());
        SseEmitter emitter = sseEventBroker.subscribe(userId, esPersonal);
        return ResponseEntity.ok(emitter);
    }
}
