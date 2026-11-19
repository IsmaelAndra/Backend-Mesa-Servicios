package com.mesaayuda.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MAPI responde primero (V49): cuando se crea un ticket, y si en Administración
 * → Inteligencia artificial está activo "MAPI responde primero", MAPI le propone
 * al solicitante pasos para resolverlo solo. Si le sirven, pulsa "Sí, se
 * resolvió" y el ticket queda resuelto sin ocupar a un agente; si no, sigue con
 * el agente como siempre (el agente ve la propuesta en el chat).
 *
 * <p>Va en segundo plano tras el commit: la IA tarda segundos y quien abre el
 * ticket no espera. Los tickets confidenciales no se envían a la IA.
 */
@Service
public class MapiPrimeroService {

    private static final Logger log = LoggerFactory.getLogger(MapiPrimeroService.class);

    private final TicketService tickets;
    private final AiAssistantService ia;

    public MapiPrimeroService(TicketService tickets, AiAssistantService ia) {
        this.tickets = tickets;
        this.ia = ia;
    }

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void alCrearTicket(TicketService.TicketCreadoEvento evento) {
        try {
            tickets.datosParaMapi(evento.ticketId())
                    .filter(d -> !d.confidencial())
                    .filter(d -> "NUEVO".equals(d.estado()) || "ASIGNADO".equals(d.estado()) || d.estado() == null)
                    .ifPresent(d -> ia.respuestaInicial(d.titulo(), d.descripcion(), d.categoria())
                            .ifPresent(md -> tickets.publicarRespuestaMapi(d.id(), aHtml(md))));
        } catch (RuntimeException e) {
            // Nunca rompe nada: el ticket sigue su curso con el agente.
            log.warn("MAPI no pudo responder primero al ticket {}: {}", evento.ticketId(), e.getMessage());
        }
    }

    private static final Pattern NEGRITA = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final Pattern NUMERADA = Pattern.compile("^\\s*\\d+[.)]\\s+(.*)$");
    private static final Pattern VINETA = Pattern.compile("^\\s*[-*•]\\s+(.*)$");

    /**
     * Markdown sencillo de la IA → HTML del chat. Todo se escapa primero; solo se
     * generan p, ol, ul, li, b y br (lo mismo que un mensaje escrito en el chat).
     */
    static String aHtml(String md) {
        StringBuilder html = new StringBuilder();
        List<String> lista = new ArrayList<>();
        String tipoLista = null;
        for (String linea : md.replace("\r", "").split("\n")) {
            Matcher n = NUMERADA.matcher(linea);
            Matcher v = VINETA.matcher(linea);
            String tipo = n.matches() ? "ol" : v.matches() ? "ul" : null;
            if (tipo == null || !tipo.equals(tipoLista)) {
                cerrarLista(html, lista, tipoLista);
                tipoLista = null;
            }
            if (tipo != null) {
                tipoLista = tipo;
                lista.add(enLinea(tipo.equals("ol") ? n.group(1) : v.group(1)));
            } else if (!linea.isBlank()) {
                String t = linea.replaceFirst("^#+\\s*", "");
                html.append("<p>").append(enLinea(t)).append("</p>");
            }
        }
        cerrarLista(html, lista, tipoLista);
        return html.toString();
    }

    private static void cerrarLista(StringBuilder html, List<String> lista, String tipo) {
        if (tipo == null || lista.isEmpty()) {
            lista.clear();
            return;
        }
        html.append('<').append(tipo).append('>');
        lista.forEach(li -> html.append("<li>").append(li).append("</li>"));
        html.append("</").append(tipo).append('>');
        lista.clear();
    }

    private static String enLinea(String s) {
        String e = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
        return NEGRITA.matcher(e).replaceAll("<b>$1</b>");
    }
}
