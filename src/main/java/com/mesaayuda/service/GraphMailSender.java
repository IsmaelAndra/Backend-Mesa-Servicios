package com.mesaayuda.service;

import com.mesaayuda.dto.MicrosoftGraphConfigResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Envío de correo por Microsoft Graph.
 * <p>
 * Vive en su propia clase a propósito. Antes el método marcado con
 * {@code @Async} estaba en {@code NotificationService}, que es justamente la
 * clase que lo invocaba: Spring solo aplica el proxy de asincronía cuando la
 * llamada atraviesa el proxy, y una llamada interna dentro de la misma clase se
 * resuelve con {@code this}, no con el proxy. El resultado era que el envío
 * nunca salía del hilo de la petición, y cada alta de ticket montaba una
 * llamada HTTP a Graph (con sus 20 s de timeout) dentro de la transacción que
 * había abierto el propio servicio, Reteniendo una conexión del pool.
 * <p>
 * Además el método se tragaba la excepción, así que el scheduler de reintentos
 * marcaba las notificaciones como enviadas aunque el correo nunca hubiera
 * salido. Aquí se separa el envío "en segundo plano" (no le importa el
 * resultado a nadie) del envío síncrono (necesario para poder marcar
 * SENT/FAILED con honestidad).
 */
@Service
public class GraphMailSender {

    private static final Logger log = LoggerFactory.getLogger(GraphMailSender.class);

    private final MicrosoftGraphService graphService;
    private final MicrosoftGraphConfigService graphConfigService;

    public GraphMailSender(MicrosoftGraphService graphService,
                           MicrosoftGraphConfigService graphConfigService) {
        this.graphService = graphService;
        this.graphConfigService = graphConfigService;
    }

    /**
     * Envío "dispara y olvida". No propaga excepciones: el llamador no espera
     * al correo y un fallo de envío no debe tumbar la operación de negocio que
     * lo disparó en primer lugar.
     */
    @Async("taskExecutor")
    public void enviarEnSegundoPlano(String to, String subject, String bodyHtml) {
        try {
            enviar(to, subject, bodyHtml);
        } catch (Exception e) {
            log.error("No se pudo enviar el correo a {} (asunto: {}): {}", to, subject, e.getMessage(), e);
        }
    }

    /**
     * Envío síncrono. Lanza si el envío falla, para que quien lo llame pueda
     * registrar el estado real. Si el correo no está habilitado devuelve
     * {@code false} sin lanzar: no es un fallo, simplemente no hay a quién enviar.
     */
    public boolean enviar(String to, String subject, String bodyHtml) throws Exception {
        MicrosoftGraphConfigResponse config = graphConfigService.getConfig();
        if (!config.enabled() || !config.mailEnabled()
                || config.mailFromAccount() == null || config.mailFromAccount().isBlank()) {
            log.debug("Correo no habilitado o sin cuenta remitente. Destinatario={}, Asunto={}", to, subject);
            return false;
        }

        Map<String, Object> result = graphService.sendMail(config.mailFromAccount(), to, subject, bodyHtml);
        if (!Boolean.TRUE.equals(result.get("success"))) {
            throw new IllegalStateException("Graph rechazó el envío: " + result.get("message"));
        }
        return true;
    }
}
