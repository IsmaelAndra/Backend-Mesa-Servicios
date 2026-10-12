package com.mesaayuda.workflow.handler;

import com.mesaayuda.service.GraphMailSender;
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

    private final GraphMailSender mailSender;

    public EmailHandler(GraphMailSender mailSender) {
        this.mailSender = mailSender;
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

        try {
            boolean enviado = mailSender.enviar(para, asunto, cuerpo);
            if (!enviado) {
                // El envío falla sin excepción en varios casos de Graph. Queda
                // registrado en la traza del nodo para poder reenviar a mano.
                log.warn("Graph no confirmó el envío del nodo EMAIL '{}' a {}", nodo.id(), para);
                return WorkflowResult.fallarReintentable("Microsoft Graph no confirmó el envío a " + para + ".");
            }
            log.info("El nodo EMAIL '{}' envió correo a {}", nodo.id(), para);
            contexto.resultado(nodo, "para", para);
            return WorkflowResult.avanzar();
        } catch (Exception e) {
            return WorkflowResult.fallarReintentable("No se pudo enviar el correo a " + para + ": " + e.getMessage());
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