package com.mesaayuda.workflow.handler;

import com.mesaayuda.entity.MailOutbox;
import com.mesaayuda.service.MailOutboxService;
import com.mesaayuda.workflow.WorkflowContext;
import com.mesaayuda.workflow.WorkflowNodeDef;
import com.mesaayuda.workflow.WorkflowNodeHandler;
import com.mesaayuda.workflow.WorkflowNodeType;
import com.mesaayuda.workflow.WorkflowResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Correo a través de Microsoft Graph.
 *
 * <p>El sistema no usa SMTP, así que el nodo delega en {@code GraphMailSender}.
 * No se elige un servidor de salida ni se construye un mensaje a mano.
 *
 * <p><b>La dirección se valida antes de enviar.</b> Un destino se lee de la
 * configuración del workflow, y una configuración con un destino mal escrito
 * haría que el correo se fuera a otra parte o rebotara. Se rechaza con un motivo
 * legible en vez de confiar en el servidor de correo para descubrirlo.
 */
@Component
public class EmailHandler implements WorkflowNodeHandler {

    private static final Logger log = LoggerFactory.getLogger(EmailHandler.class);

    /** Acepta solo lo que un servidor de correo acepta en un destino. */
    private static final Pattern CORREO_VALIDO =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final MailOutboxService outbox;

    public EmailHandler(MailOutboxService outbox) {
        this.outbox = outbox;
    }

    @Override
    public WorkflowNodeType tipo() {
        return WorkflowNodeType.EMAIL;
    }

    @Override
    public WorkflowResult ejecutar(WorkflowContext contexto, WorkflowNodeDef nodo) {
        Map<String, Object> config = nodo.config() == null ? Map.of() : nodo.config();

        // Admiten variables: "para" puede ser {{ticket.solicitanteCorreo}}, y el
        // cuerpo se escapa para HTML porque el título de un ticket lo escribe el
        // solicitante y no debe poder meter marcado en un correo institucional.
        String para = contexto.configTexto(nodo, "para");
        String asunto = contexto.configTexto(nodo, "asunto");
        String cuerpo = texto(config.get("cuerpo"));
        cuerpo = cuerpo == null ? null : contexto.interpolarHtml(cuerpo);

        if (para == null) {
            return WorkflowResult.fallar("El nodo EMAIL '" + nodo.id() + "' no tiene destinatario.");
        }
        if (!CORREO_VALIDO.matcher(para).matches()) {
            // Se comprueba aquí porque el error de Graph no diría qué campo del
            // workflow estaba mal.
            return WorkflowResult.fallar("El nodo EMAIL '" + nodo.id() + "' tiene un destinatario que "
                    + "no es un correo válido: '" + para + "'.");
        }
        if (asunto == null || cuerpo == null) {
            return WorkflowResult.fallar("El nodo EMAIL '" + nodo.id() + "' necesita asunto y cuerpo.");
        }

        // A la cola de correo, como el resto: así un workflow masivo tampoco
        // supera el límite del buzón. El envío y sus reintentos se ven en el
        // panel de la cola; el nodo da por hecho su trabajo al encolarlo.
        if (!outbox.correoHabilitado()) {
            return WorkflowResult.fallar("El nodo EMAIL '" + nodo.id() + "' no puede enviar: el correo está "
                    + "deshabilitado en la configuración de Microsoft Graph.");
        }
        try {
            outbox.encolar(para, asunto, cuerpo, "WORKFLOW", MailOutbox.PRIORIDAD_NORMAL, null, null);
            log.info("El nodo EMAIL '{}' encoló un correo para {}", nodo.id(), para);
            contexto.resultado(nodo, "para", para);
            contexto.resultado(nodo, "estado", "ENCOLADO");
            return WorkflowResult.avanzar();
        } catch (Exception e) {
            return WorkflowResult.fallarReintentable("No se pudo encolar el correo a " + para + ": " + e.getMessage());
        }
    }

    private String texto(Object valor) {
        if (valor == null) {
            return null;
        }
        String s = String.valueOf(valor);
        return s.isBlank() ? null : s;
    }
}