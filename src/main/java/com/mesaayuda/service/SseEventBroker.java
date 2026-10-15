package com.mesaayuda.service;
import java.util.UUID;

import com.mesaayuda.dto.TicketEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class SseEventBroker {

    private static final Logger log = LoggerFactory.getLogger(SseEventBroker.class);
    private static final int MAX_RECENT = 40;

    /**
     * Un usuario puede tener varias conexiones vivas (pestañas, otros equipos).
     * Antes se guardaba un solo emisor por id: al abrir una segunda pestaña la
     * sobrescribía a la primera, y al cerrar la primera su onCompletion borraba la
     * entrada, dejando al usuario marcado como desconectado con la segunda abierta.
     */
private final Map<UUID, Set<SseEmitter>> clients = new ConcurrentHashMap<>();

    /**
     * Qué usuarios son personal de la mesa de servicios.
     *
     * <p>Hace falta para dirigir los eventos de ticket. La regla es la misma que
     * aplica {@code TicketAccessPolicy} sobre un recurso: el personal ve la cola
     * completa porque el kanban no funciona de otro modo, y un solicitador solo
     * recibe los eventos de sus propios tickets.
     *
     * <p>Antes no hacia falta: {@code publish} recorria todos los clientes sin
     * mirar quien estaba conectado, de modo que cualquier usuario con sesion
     * recibia en tiempo real el titulo y el estado de todos los tickets de la
     * institucion, eludiendo la autorizacion por recurso. Vease el mismo criterio
     * ya aplicado en {@code publishTyping}.
     */
    private final Map<UUID, Boolean> personal = new ConcurrentHashMap<>();

    private final Deque<TicketEvent> recent = new ArrayDeque<>();
    private final AtomicLong seq = new AtomicLong();

    /**
     * @param esPersonal si la cuenta es agente, administrador o super administrador.
     *                    Se guarda porque el rol puede cambiar con la sesion abierta
     *                    y no se vuelve a pedir al abrir cada conexion.
     */
    public SseEmitter subscribe(UUID userId, boolean esPersonal) {
        personal.put(userId, esPersonal);
        ultimaDesconexion.remove(userId);
        SseEmitter emitter = new SseEmitter(0L);
        emitter.onCompletion(() -> desconectar(userId, emitter));
        emitter.onTimeout(() -> {
            desconectar(userId, emitter);
            emitter.complete();
        });
        emitter.onError(e -> desconectar(userId, emitter));
        clients.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(emitter);

        // Saludo inmediato. Sin esto, si no hay eventos en el bucle de reenvio no se
        // escribe NADA en la respuesta: Spring no confirma las cabeceras, el
        // EventSource del navegador nunca dispara onopen y el cliente se queda
        // pensando que no esta conectado (el punto de presencia salia ambar con la
        // sesion abierta). Un comentario no llega al onmessage del cliente, asi que
        // no altera la logica de eventos.
        sendCommentSafe(userId, emitter, "conectado");

synchronized (recent) {
              // El reenvio de lo reciente tambien se filtra. Sin esto, abrir la
              // pagina bastaba para recibir los ultimos 40 eventos de toda la
              // institucion, aunque el canal en vivo ya estuviera dirigido.
              for (TicketEvent event : recent) {
                  if (leCorresponde(userId, event)) {
                      sendSafe(userId, emitter, event);
                  }
              }
          }
          return emitter;
      }

      /**
       * Si un evento de ticket le corresponde a quien esta conectado.
       *
       * <p>El personal lo ve todo, porque su trabajo es la cola entera. Un
       * solicitador solo lo ve si el ticket es suyo, que es exactamente lo que
       * decide {@code TicketAccessPolicy.puedeLeer}.
       */
      private boolean leCorresponde(UUID userId, TicketEvent event) {
          if (Boolean.TRUE.equals(personal.get(userId))) {
              return true;
          }
          return event.ticketId() != null
                  && event.requesterId() != null
                  && event.requesterId().equals(userId);
      }

    /**
     * Latido a todos los clientes. Sin esto una conexion que se cae en silencio
     * (se cierra la laptop, se corta la red, el movil pierde senal) no genera
     * ningun error en el servidor: la sigue dando por viva y el usuario queda
     * marcado como en linea para siempre. Al escribir y fallar, se limpia.
     */
    public void latido() {
        clients.forEach((userId, emisores) -> {
            for (SseEmitter emitter : emisores) {
                sendCommentSafe(userId, emitter, "latido");
            }
        });
    }

    /** Cuándo cerró el usuario su última conexión (null si está conectado o nunca se conectó). */
    public Instant ultimaDesconexion(UUID userId) {
        return userId == null ? null : ultimaDesconexion.get(userId);
    }

    /** Momento en que cada usuario cerró su última conexión; se usa para "Reciente". */
    private final Map<UUID, Instant> ultimaDesconexion = new ConcurrentHashMap<>();

    /** true si el usuario tiene al menos una conexión SSE abierta ahora mismo. */
    public boolean isConectado(UUID userId) {
        if (userId == null) {
            return false;
        }
        Set<SseEmitter> emisores = clients.get(userId);
        return emisores != null && !emisores.isEmpty();
    }

public TicketEvent publish(String type, UUID ticketId, Long ticketNumber, String ticketTitle, String status,
                                 UUID actorId, String actorName, UUID assignedAgentId, String assignedAgentName,
                                 UUID requesterId) {
          TicketEvent event = new TicketEvent(seq.incrementAndGet(), type, ticketId, ticketNumber, ticketTitle,
                  status, actorId, actorName, assignedAgentId, assignedAgentName, requesterId,
                  Instant.now());
          synchronized (recent) {
              recent.addLast(event);
              while (recent.size() > MAX_RECENT) {
                  recent.removeFirst();
              }
          }
          clients.forEach((userId, emisores) -> {
              // Se decide por conexion y no una sola vez, porque parte de los
              // conectados son personal y parte no.
              if (!leCorresponde(userId, event)) {
                  return;
              }
              for (SseEmitter emitter : emisores) {
                  sendSafe(userId, emitter, event);
              }
          });
          return event;
      }

    /**
     * Avisa de que un usuario está escribiendo en un ticket. Va dirigido solo al
     * solicitante y al agente asignado, y nunca de vuelta a quien escribe, para
     * que no vea su propio nombre. Se manda con otro nombre de evento para que el
     * frontend pueda distinguirlo de las actualizaciones del ticket.
     */
    public void publishTyping(UUID ticketId, UUID actorId, String actorName,
                              Collection<UUID> destinatarios) {
        if (ticketId == null || actorId == null || destinatarios == null || destinatarios.isEmpty()) {
            return;
        }
        TypingEvent evento = new TypingEvent(ticketId, actorId, actorName, Instant.now());
        for (UUID userId : destinatarios) {
            if (userId == null || userId.equals(actorId)) {
                continue;
            }
            Set<SseEmitter> emisores = clients.get(userId);
            if (emisores == null) {
                continue;
            }
            for (SseEmitter emitter : emisores) {
                sendTypingSafe(userId, emitter, evento);
            }
        }
    }

    /**
     * Avisa al destinatario de que acaba de recibir una notificación. Antes el
     * navbar solo se enteraba al recargar o en el sondeo de 60 segundos, y
     * el desplegable de la campana se quedaba vacío hasta que alguien navegaba.
     * Va dirigido a un solo usuario y con otro nombre de evento para que el
     * cliente lo distinga de los cambios de ticket.
     */
    public void publishNotification(UUID userId, String type, String subject, UUID ticketId) {
        if (userId == null) {
            return;
        }
        Set<SseEmitter> emisores = clients.get(userId);
        if (emisores == null || emisores.isEmpty()) {
            return;
        }
        NotificationEvent evento = new NotificationEvent(type, subject, ticketId, Instant.now());
        for (SseEmitter emitter : emisores) {
            sendNotificationSafe(userId, emitter, evento);
        }
    }

    private void sendNotificationSafe(UUID userId, SseEmitter emitter, NotificationEvent evento) {
        try {
            emitter.send(SseEmitter.event().name("notification").data(evento));
        } catch (IOException | IllegalStateException ex) {
            try {
                emitter.complete();
            } catch (IllegalStateException ignored) {
                // el emitter ya estaba cerrado
            }
            desconectar(userId, emitter);
        }
    }

    private void sendTypingSafe(UUID userId, SseEmitter emitter, TypingEvent evento) {
        try {
            emitter.send(SseEmitter.event().name("typing").data(evento));
        } catch (IOException | IllegalStateException ex) {
            try {
                emitter.complete();
            } catch (IllegalStateException ignored) {
                // el emitter ya estaba cerrado
            }
            desconectar(userId, emitter);
        }
    }

    /**
     * Avisa a los autores de los mensajes que el usuario indicado acaba de leer.
     * <p>
     * Sin esto el doble check azul no aparecia hasta que el remitente recargaba
     * la pagina: la marca de lectura si se guardaba, pero nadie se enteraba. Es un
     * evento aparte de "ticket" porque no cambia el estado del ticket, solo los
     * acuses de lectura del hilo, y el frontend lo distingue por el nombre.
     *
     * @param autores ids de los que escribieron los mensajes ya leidos. Se
     *                ignoran los que coincidan con el propio lector, para que
     *                nadie reciba el aviso de su propia lectura.
     */
    public void publishLectura(UUID ticketId, UUID lectorId, String lectorNombre,
                               Collection<UUID> autores) {
        if (ticketId == null || lectorId == null || autores == null || autores.isEmpty()) {
            return;
        }
        LecturaEvent evento = new LecturaEvent(ticketId, lectorId, lectorNombre, Instant.now());
        for (UUID userId : autores) {
            if (userId == null || userId.equals(lectorId)) {
                continue;
            }
            Set<SseEmitter> emisores = clients.get(userId);
            if (emisores == null) {
                continue;
            }
            for (SseEmitter emitter : emisores) {
                sendLecturaSafe(userId, emitter, evento);
            }
        }
    }

    private void sendLecturaSafe(UUID userId, SseEmitter emitter, LecturaEvent evento) {
        try {
            emitter.send(SseEmitter.event().name("lectura").data(evento));
        } catch (IOException | IllegalStateException ex) {
            try {
                emitter.complete();
            } catch (IllegalStateException ignored) {
                // el emitter ya estaba cerrado
            }
            desconectar(userId, emitter);
        }
    }

    /**
     * Paso de una ejecución de workflow, para animarla en vivo en el lienzo.
     *
     * <p>Va dirigido a un solo usuario (quien la lanzó o el autor) y con su propio
     * nombre de evento. Quien llama ya comprobó que es super administrador: el
     * payload lleva la salida del nodo, igual que el historial de ejecuciones.
     */
    public void publishWorkflow(UUID userId, Object paso) {
        if (userId == null || paso == null) {
            return;
        }
        Set<SseEmitter> emisores = clients.get(userId);
        if (emisores == null || emisores.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : emisores) {
            try {
                emitter.send(SseEmitter.event().name("workflow").data(paso));
            } catch (IOException | IllegalStateException ex) {
                try {
                    emitter.complete();
                } catch (IllegalStateException ignored) {
                    // el emitter ya estaba cerrado
                }
                desconectar(userId, emitter);
            }
        }
    }

    public List<TicketEvent> recentEvents() {
        synchronized (recent) {
            return new ArrayList<>(recent);
        }
    }

    /** Quita una conexión; el usuario solo deja el mapa cuando se queda sin ninguna. */
private void desconectar(UUID userId, SseEmitter emitter) {
          clients.computeIfPresent(userId, (k, emisores) -> {
              emisores.remove(emitter);
              return emisores.isEmpty() ? null : emisores;
          });
          if (!clients.containsKey(userId)) {
              // Para el estado "Reciente": cuándo cerró su última pestaña.
              ultimaDesconexion.put(userId, Instant.now());
          }
          // El rol se guarda solo mientras haya conexiones: si no, el mapa
          // crecia con cada alta o baja de usuario y nunca se limpiaba.
          if (!clients.containsKey(userId)) {
              personal.remove(userId);
          }
      }

    private void sendCommentSafe(UUID userId, SseEmitter emitter, String texto) {
        try {
            emitter.send(SseEmitter.event().comment(texto));
        } catch (IOException | IllegalStateException ex) {
            try {
                emitter.complete();
            } catch (IllegalStateException ignored) {
                // el emitter ya estaba cerrado
            }
            desconectar(userId, emitter);
        }
    }

private void sendSafe(UUID userId, SseEmitter emitter, TicketEvent event) {
          try {
              // paraCliente() quita el id del solicitante: se uso para dirigir el
              // evento y no hace falta que el navegador lo conozca.
              emitter.send(SseEmitter.event().name("ticket").id(String.valueOf(event.seq()))
                      .data(event.paraCliente()));
          } catch (IOException | IllegalStateException ex) {
            try {
                emitter.complete();
            } catch (IllegalStateException ignored) {
                // el emitter ya estaba cerrado
            }
            desconectar(userId, emitter);
        }
    }

    /** Payload del evento de escritura. Viaja aparte para no mezclarlo con TicketEvent. */
    public record TypingEvent(UUID ticketId, UUID userId, String displayName, Instant at) {
    }

    /** Payload del aviso de notificacion nueva. */
    public record NotificationEvent(String type, String subject, UUID ticketId, Instant at) {
    }

    /**
     * Payload del acuse de lectura. No lleva la lista de mensajes a los que
     * aplica: el frontend, al recibirlo, vuelve a pedir la ultima pagina del
     * hilo y ya recibe los contadores y los nombres desde el servidor, de modo
     * que el cliente no decide nada por su cuenta.
     */
    public record LecturaEvent(UUID ticketId, UUID userId, String displayName, Instant at) {
    }
}
