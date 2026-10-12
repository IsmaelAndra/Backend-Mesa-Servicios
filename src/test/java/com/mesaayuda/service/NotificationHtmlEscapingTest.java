package com.mesaayuda.service;

import static com.mesaayuda.IdsPrueba.id;
import com.mesaayuda.entity.Area;
import com.mesaayuda.entity.Role;
import com.mesaayuda.entity.Ticket;
import com.mesaayuda.entity.User;
import com.mesaayuda.dto.MicrosoftGraphConfigResponse;
import com.mesaayuda.repository.AreaRepository;
import com.mesaayuda.repository.BrandingConfigRepository;
import com.mesaayuda.repository.CategoryRepository;
import com.mesaayuda.repository.NotificationRepository;
import com.mesaayuda.repository.NotificationTemplateRepository;
import com.mesaayuda.repository.PriorityRepository;
import com.mesaayuda.repository.StatusRepository;
import com.mesaayuda.repository.SucursalRepository;
import com.mesaayuda.repository.SupportTeamRepository;
import com.mesaayuda.repository.TicketRepository;
import com.mesaayuda.repository.TicketTypeRepository;
import com.mesaayuda.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Escapado del HTML en los correos de notificacion.
 *
 * <p>El cuerpo se envia como HTML a Microsoft Graph y las variables se rellenan
 * con datos que escribe cualquier solicitante: el titulo de un ticket, la
 * descripcion y el texto de una respuesta. Sin escapar, un titulo con
 * {@code <img src=x onerror=...>} llegaba renderizado a los agentes y a la
 * persona que lo reporto.
 *
 * <p>Los clientes de correo no ejecutan {@code <script>}, pero si renderizan
 * {@code onerror}, {@code svg onload} y los enlaces: el resultado practico es
 * phishing con el remitente de la institucion.
 *
 * <p>Se cubren los dos caminos por los que llega texto al cuerpo. El primero es
 * el de las plantillas, que pasa por el relleno con escapado. El segundo son los
 * textos de reserva que se arman con {@code String.formatted} cuando no hay
 * plantilla en base: nunca pasaron por ahi, y son los que se ejecutan en una
 * instalacion recien sembrada.
 */
@ExtendWith(MockitoExtension.class)
class NotificationHtmlEscapingTest {

    /** Payload clasico de HTML inyectado en correo. */
    private static final String PAYLOAD = "<img src=x onerror=\"alert(1)\">";

    /** Version SVG, que ademas es lo que el navegador ejecuta aunque no haya onerror. */
    private static final String PAYLOAD_SVG = "<svg onload=alert(1)>";

    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationTemplateRepository templateRepository;
    @Mock private GraphMailSender graphMailSender;
    @Mock private SseEventBroker sseEventBroker;
    @Mock private MicrosoftGraphConfigService graphConfigService;
    @Mock private UserRepository userRepository;
    @Mock private TicketRepository ticketRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private PriorityRepository priorityRepository;
    @Mock private StatusRepository statusRepository;
    @Mock private TicketTypeRepository ticketTypeRepository;
    @Mock private SupportTeamRepository teamRepository;
    @Mock private AreaRepository areaRepository;
    // El correo se envuelve con el logo de la marca; sin este mock, el
    // recorrido revienta al construir el cuerpo.
    @Mock private BrandingConfigRepository brandingConfigRepository;
    @Mock private SucursalRepository sucursalRepository;

    @InjectMocks private NotificationService service;

    private User agente;
    private Ticket ticket;

    @BeforeEach
    void setUp() {
        // Sin plantillas en base: se ejecuta el texto de reserva, que es el camino
        // que se construia con String.formatted y no escapaba nada.
        lenient().when(templateRepository.findByNameTemplateAndActiveTemplateTrue(anyString()))
                .thenReturn(Optional.empty());
        // El envio solo se dispara si la configuracion de Graph lo permite.
        lenient().when(graphConfigService.getConfig()).thenReturn(graficoConCorreo());

        Role rolAgente = new Role();
        rolAgente.setNameRole("AGENT");
        agente = new User();
        agente.setIdUser(id(20));
        agente.setCorporateEmailUser("agente@coacmagisterio.fin.ec");
        agente.setDisplayName("Agente de Prueba");
        agente.setRolUser(rolAgente);

        Area area = new Area();
        area.setIdArea(id(1));
        area.setNameArea("TI");

        ticket = new Ticket();
        ticket.setIdTicket(id(7));
        ticket.setUserTicket(agente);
        ticket.setAgentTicket(agente);
        ticket.setAreaTicket(area);
    }

    /** Cuerpo HTML con el que se despacha el correo. */
    private String cuerpoEnviado() {
        ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
        verify(graphMailSender).enviarEnSegundoPlano(anyString(), anyString(), cap.capture());
        return cap.getValue();
    }

    @Test
    @DisplayName("el titulo malicioso no llega como HTML en el correo de creacion")
    void tituloNoSeInyectaEnElCorreoDeCreacion() {
        ticket.setTitleTicket(PAYLOAD);
        ticket.setDescriptionTicket("problema");

        service.notifyTicketCreated(ticket);

        String cuerpo = cuerpoEnviado();
        assertFalse(cuerpo.contains("<img"),
                "el titulo del ticket llego como etiqueta al correo: " + cuerpo);
        assertTrue(cuerpo.contains("&lt;img"));
    }

    @Test
    @DisplayName("el mensaje de una respuesta tampoco se inyecta")
    void mensajeDeRespuestaNoSeInyecta() {
        ticket.setTitleTicket("Impresora");
        ticket.setDescriptionTicket("no imprime");

        service.notifyTicketUpdated(ticket, agente, PAYLOAD);

        String cuerpo = cuerpoEnviado();
        assertFalse(cuerpo.contains("<img"), "el mensaje llego como etiqueta: " + cuerpo);
        assertTrue(cuerpo.contains("&lt;img"));
    }

    @Test
    @DisplayName("un SVG con onload tampoco se inyecta")
    void svgNoSeInyecta() {
        ticket.setTitleTicket(PAYLOAD_SVG);
        ticket.setDescriptionTicket("x");

        service.notifyTicketCreated(ticket);

        String cuerpo = cuerpoEnviado();
        assertFalse(cuerpo.contains("<svg"), "un SVG llego sin escapar: " + cuerpo);
        assertTrue(cuerpo.contains("&lt;svg"));
    }

    @Test
    @DisplayName("el titulo de una tarea tampoco se inyecta")
    void tituloDeTareaNoSeInyecta() {
        com.mesaayuda.entity.Task task = new com.mesaayuda.entity.Task();
        task.setTitleTask(PAYLOAD);
        task.setDescriptionTask(PAYLOAD_SVG);
        task.setAssignedUser(agente);
        task.setStatusTask("PENDIENTE");

        service.notifyTaskAssigned(task);

        String cuerpo = cuerpoEnviado();
        assertFalse(cuerpo.contains("<img"), "el titulo de la tarea llego como etiqueta: " + cuerpo);
        assertFalse(cuerpo.contains("<svg"), "la descripcion de la tarea llego como etiqueta: " + cuerpo);
    }

    @Test
    @DisplayName("el texto legitimo con acentos y signos se conserva")
    void elTextoLegitimoNoSeAltera() {
        ticket.setTitleTicket("Impresora rota en el piso 2 (café) - 50% de fallos");
        ticket.setDescriptionTicket("x");

        service.notifyTicketCreated(ticket);

        String cuerpo = cuerpoEnviado();
        // Los acentos y el parentesis deben seguir visibles: escapar es para los
        // caracteres reservados, no para mutilar el texto.
        assertTrue(cuerpo.contains("café"), "no llego el acento. Cuerpo: [" + cuerpo + "]");
        assertTrue(cuerpo.contains("50%"), "no llego el porcentaje. Cuerpo: [" + cuerpo + "]");
        assertTrue(cuerpo.contains("(café)"), "no llego el parentesis. Cuerpo: [" + cuerpo + "]");
    }

    @Test
    @DisplayName("el escapado de las plantillas tampoco deja pasar HTML")
    void plantillaTambienEscapa() {
        // Con plantilla activa, el cuerpo pasa por el relleno y no por el texto de
        // reserva. Se comprueba que los dos caminos estan cubiertos.
        com.mesaayuda.entity.NotificationTemplate plantilla = new com.mesaayuda.entity.NotificationTemplate();
        plantilla.setNameTemplate("TICKET_CREADO");
        plantilla.setActiveTemplate(true);
        plantilla.setSubjectTemplate("Ticket #[id] creado");
        plantilla.setBodyTemplate("<p>Estimado/a [nombre], su ticket <b>[titulo]</b> esta listo.</p>");
        when(templateRepository.findByNameTemplateAndActiveTemplateTrue("TICKET_CREADO"))
                .thenReturn(Optional.of(plantilla));

        ticket.setTitleTicket(PAYLOAD);
        ticket.setDescriptionTicket("x");

        service.notifyTicketCreated(ticket);

        String cuerpo = cuerpoEnviado();
        assertFalse(cuerpo.contains("<img"), "la plantilla dejo pasar HTML: " + cuerpo);
        assertTrue(cuerpo.contains("&lt;img"));
    }

  /** Configuracion de Graph con el correo habilitado, para que se llegue al envio. */
  private static MicrosoftGraphConfigResponse graficoConCorreo() {
      return new MicrosoftGraphConfigResponse(
              id(1),                         // id
              "tenant",                   // tenantId
              "app-id",                   // clientId
              true,                       // clientSecretConfigured
              "https://graph.microsoft.com/.default", // scope
              "client_credentials",       // grantType
              null,                       // secretExpiresAt
              true,                       // enabled
              10,                         // syncIntervalMinutes
              true,                       // mailEnabled
              "messaging@coac.gob.ec",    // mailFromAccount
              null,                       // lastSyncAt
              null,                       // updatedAt
              "CONNECTED");               // status
  }
}