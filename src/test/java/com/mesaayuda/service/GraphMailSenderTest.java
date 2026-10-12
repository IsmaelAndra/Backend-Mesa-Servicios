package com.mesaayuda.service;

import static com.mesaayuda.IdsPrueba.id;
import com.mesaayuda.dto.MicrosoftGraphConfigResponse;
import com.mesaayuda.exception.MailDeliveryException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El envío de correo tenía dos fallos encadenados:
 * <p>
 * 1) El método marcado con {@code @Async} se llamaba a sí mismo desde la misma
 * clase, así que nunca se ejecutaba fuera del hilo de la petición: cada alta de
 * ticket bloqueaba una conexión del pool durante la llamada a Graph.
 * 2) Se tragaba la excepción, de modo que el planificador de reintentos marcaba
 * la notificación como SENT aunque el correo nunca hubiera salido.
 */
@ExtendWith(MockitoExtension.class)
class GraphMailSenderTest {

    @Mock
    private MicrosoftGraphService graphService;

    @Mock
    private MicrosoftGraphConfigService graphConfigService;

    @InjectMocks
    private GraphMailSender sender;

    private MicrosoftGraphConfigResponse config(boolean enabled, boolean mailEnabled, String fromAccount) {
        return new MicrosoftGraphConfigResponse(
                id(1), "tenant", "client", true, "https://graph.microsoft.com/.default",
                "client_credentials", null, enabled, 60, mailEnabled, fromAccount,
                null, null, enabled ? "CONNECTED" : "DISABLED");
    }

    private MicrosoftGraphConfigResponse config(boolean enabled, boolean mailEnabled) {
        return config(enabled, mailEnabled, "correo@coacmagisterio.fin.ec");
    }

    @Test
    @DisplayName("Envía el correo y devuelve true cuando Graph responde con éxito")
    void enviaYConfirma() throws Exception {
        when(graphConfigService.getConfig()).thenReturn(config(true, true));
        when(graphService.sendMail(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Map.of("success", true));

        assertThat(sender.enviar("destino@coacmagisterio.fin.ec", "Asunto", "<p>Hola</p>")).isTrue();

        verify(graphService).sendMail(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Si Graph rechaza el envío, propaga el fallo en lugar de tragárselo")
    void propagaFalloDeGraph() {
        when(graphConfigService.getConfig()).thenReturn(config(true, true));
        when(graphService.sendMail(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Map.of("success", false, "message", "ErrorInvalidRecipients"));

        // Antes esto devolvía en silencio y el llamador marcaba la notificación
        // como enviada. Ahora el error tiene que llegar al llamador.
        assertThatThrownBy(() -> sender.enviar("destino@coacmagisterio.fin.ec", "Asunto", "<p>Hola</p>"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ErrorInvalidRecipients");
    }

    @Test
    @DisplayName("Si el correo está deshabilitado no lanza: no es un fallo, no hay a quién enviar")
    void correoDeshabilitadoNoEsFallo() throws Exception {
        when(graphConfigService.getConfig()).thenReturn(config(false, false));

        assertThat(sender.enviar("destino@coacmagisterio.fin.ec", "Asunto", "<p>Hola</p>")).isFalse();

        verify(graphService, never()).sendMail(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Si falta la cuenta remitente, no intenta enviar")
    void sinCuentaRemitenteNoEnvia() throws Exception {
        when(graphConfigService.getConfig())
                .thenReturn(config(true, true, "  "));

        assertThat(sender.enviar("destino@coacmagisterio.fin.ec", "Asunto", "<p>Hola</p>")).isFalse();

        verify(graphService, never()).sendMail(any(), any(), any(), any());
    }

    @Test
    @DisplayName("El envío en segundo plano nunca propaga el fallo al llamador")
    void segundoPlanoNoPropaga() {
        when(graphConfigService.getConfig()).thenReturn(config(true, true));
        when(graphService.sendMail(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("timeout contra Graph"));

        // El disparo de la notificación no debe romperse porque el correo falle.
        org.assertj.core.api.Assertions.assertThatCode(
                        () -> sender.enviarEnSegundoPlano("d@coacmagisterio.fin.ec", "Asunto", "<p>x</p>"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("MailDeliveryException conserva la causa original")
    void mailDeliveryExceptionConservaCausa() {
        Exception original = new IllegalStateException("fallo de red");

        assertThatThrownBy(() -> {
            throw new MailDeliveryException("No se pudo enviar", original);
        })
                .isInstanceOf(MailDeliveryException.class)
                .hasCause(original);
    }
}
