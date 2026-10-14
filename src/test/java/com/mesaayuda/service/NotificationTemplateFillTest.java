package com.mesaayuda.service;

import java.util.UUID;
import static com.mesaayuda.IdsPrueba.id;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El reemplazo de variables de las plantillas. Estas pruebas fijan dos errores
 * reales que llegaron a producción: las plantillas de data.sql usan {{id}} y
 * solo se reconocía [id], así que el usuario recibía los marcadores literales.
 */
class NotificationTemplateFillTest {

    private NotificationService service;
    private com.mesaayuda.repository.BrandingConfigRepository brandingRepo;

    @BeforeEach
    void setUp() {
        service = new NotificationService();
        brandingRepo = Mockito.mock(com.mesaayuda.repository.BrandingConfigRepository.class);
        ReflectionTestUtils.setField(service, "templateRepository",
                Mockito.mock(com.mesaayuda.repository.NotificationTemplateRepository.class));
        ReflectionTestUtils.setField(service, "brandingConfigRepository", brandingRepo);
    }

    private String fill(String text, Map<String, Object> variables) throws Exception {
        java.lang.reflect.Method m = NotificationService.class
                .getDeclaredMethod("fill", String.class, Map.class);
        m.setAccessible(true);
        return (String) m.invoke(service, text, variables);
    }

/**
 * La imagen que se sube a una plantilla se usa desde el HTML del cuerpo con
 * [imagenUrl]. La ruta guardada es relativa ("/templates/image/token"), pero el
 * correo se abre fuera del navegador y sin sesion, asi que necesita la URL
 * absoluta: con la ruta suelta la imagen no se veria nunca.
 */
@Test
void laImagenDeLaPlantillaSeRellenaConUrlAbsoluta() throws Exception {
ReflectionTestUtils.setField(service, "appBaseUrl", "https://mesa.coac.gob.ec/api");

String cuerpo = fillConPlantilla(
"<p>Hola [nombre]</p><img src=\"[imagenUrl]\" />",
Map.of("nombre", "Ana"),
plantilla("TICKET_CREADO", "/templates/image/0123456789abcdef0123456789abcdef"));

assertTrue(cuerpo.contains("<p>Hola Ana</p>"), cuerpo);
assertTrue(cuerpo.contains("src=\"https://mesa.coac.gob.ec/api/templates/image/0123456789abcdef0123456789abcdef\""), cuerpo);
// El token no debe quedar a la vista en el correo.
assertFalse(cuerpo.contains("[imagenUrl]"), cuerpo);
}

/**
 * Solo se ofrece [imagenUrl], no una etiqueta img montada: quien escribe el
 * HTML es el administrador y decide la etiqueta, el ancho y los estilos.
 */
@Test
void laVariableImagenDejaDeExistir() throws Exception {
ReflectionTestUtils.setField(service, "appBaseUrl", "https://mesa.coac.gob.ec/api");

String cuerpo = fillConPlantilla(
"<p>[imagen]</p>",
Map.of(),
plantilla("TICKET_CREADO", "/templates/image/0123456789abcdef0123456789abcdef"));

assertEquals("<p></p>", cuerpo);
}

@Test
void imagenUrlDevuelveSoloLaDireccion() throws Exception {
ReflectionTestUtils.setField(service, "appBaseUrl", "https://mesa.coac.gob.ec/api");

String cuerpo = fillConPlantilla(
"<img src=\"[imagenUrl]\" />",
Map.of(),
plantilla("TICKET_CREADO", "/templates/image/0123456789abcdef0123456789abcdef"));

assertEquals("<img src=\"https://mesa.coac.gob.ec/api/templates/image/0123456789abcdef0123456789abcdef\" />", cuerpo);
}

@Test
void unaPlantillaSinImagenDejaLosTokensVaciosYNoRotos() throws Exception {
ReflectionTestUtils.setField(service, "appBaseUrl", "https://mesa.coac.gob.ec/api");

String cuerpo = fillConPlantilla(
"Hola [nombre][imagenUrl]",
Map.of("nombre", "Ana"),
plantilla("TICKET_CREADO", null));

assertEquals("Hola Ana", cuerpo);
}

/** Una URL que ya viene completa no se le vuelve a pegar la base. */
@Test
void unaImagenYaAbsolutaNoSeDuplica() throws Exception {
ReflectionTestUtils.setField(service, "appBaseUrl", "https://mesa.coac.gob.ec/api");

String cuerpo = fillConPlantilla(
"[imagenUrl]",
Map.of(),
plantilla("TICKET_CREADO", "https://cdn.coac.gob.ec/img/aviso.png"));

assertEquals("https://cdn.coac.gob.ec/img/aviso.png", cuerpo);
}

/** Sin base de API configurada la ruta relativa no se puede resolver. */
@Test
void sinBaseDeLaApiLaImagenRelativaNoSePublica() throws Exception {
ReflectionTestUtils.setField(service, "appBaseUrl", "");

String cuerpo = fillConPlantilla(
"[imagenUrl]",
Map.of(),
plantilla("TICKET_CREADO", "/templates/image/0123456789abcdef0123456789abcdef"));

assertEquals("", cuerpo);
}

/**
 * [logo] entrega la URL a secas, no la etiqueta &lt;img&gt;.
 *
 * <p>El relleno escapa todo lo que inserta, asi que cuando el valor era el HTML
 * del logo completo llegaba al correo como texto literal: el encabezado salia con
 * `&lt;img src=&quot;...&quot;` escrito dentro en vez de la imagen. Quien escribe
 * la plantilla pone la etiqueta, igual que con [imagenUrl].
 */
@Test
void elLogoSeRellenaConLaUrlSola() throws Exception {
ReflectionTestUtils.setField(service, "appBaseUrl", "https://mesa.coac.gob.ec/api");
conLogo("/uploads/publico/logo_c42c712b.png");

String cuerpo = fillConPlantilla(
"<img src=\"[logo]\" alt=\"Mesa de Ayuda COAC\" style=\"max-height:60px\" />",
Map.of(),
plantilla("TICKET_CREADO", null));

assertEquals("<img src=\"https://mesa.coac.gob.ec/api/uploads/publico/logo_c42c712b.png\""
+ " alt=\"Mesa de Ayuda COAC\" style=\"max-height:60px\" />", cuerpo);
}

/** El valor de [logo] nunca debe traer una etiqueta, aunque se quede sin comillas. */
@Test
void elLogoNoSeRellenaConHtml() throws Exception {
ReflectionTestUtils.setField(service, "appBaseUrl", "https://mesa.coac.gob.ec/api");
conLogo("/uploads/publico/logo_c42c712b.png");

Map<String, Object> comunes = comunes(Map.of());

assertEquals("https://mesa.coac.gob.ec/api/uploads/publico/logo_c42c712b.png", comunes.get("logo"));
}

/**
 * Una URL de logo escrita a mano con comillas no puede cerrar el atributo src ni
 * colar un onerror en el correo institucional: el escapado lo neutralize.
 */
@Test
void unaUrlDeLogoConComillasNoRompeElAtributo() throws Exception {
ReflectionTestUtils.setField(service, "appBaseUrl", "");
conLogo("/logo.png\" onerror=\"alert(1)");

String cuerpo = fillConPlantilla("<img src=\"[logo]\" />", Map.of(), plantilla("TICKET_CREADO", null));

assertEquals("<img src=\"/logo.png&quot; onerror=&quot;alert(1)\" />", cuerpo);
assertFalse(cuerpo.contains("onerror=\"alert"), cuerpo);
}

/** Sin logo configurado la variable queda vacia y no aparece un token suelto. */
@Test
void sinLogoConfiguradoLaVariableQuedaVacia() throws Exception {
ReflectionTestUtils.setField(service, "appBaseUrl", "https://mesa.coac.gob.ec/api");

String cuerpo = fillConPlantilla("<img src=\"[logo]\" />", Map.of(), plantilla("TICKET_CREADO", null));

assertEquals("<img src=\"\" />", cuerpo);
assertFalse(cuerpo.contains("[logo]"), cuerpo);
}

/** Deja configurado un logo de marca para el servicio en pruebas. */
private void conLogo(String logoUrl) {
com.mesaayuda.entity.BrandingConfig branding = new com.mesaayuda.entity.BrandingConfig();
branding.setIdBranding(UUID.randomUUID());
branding.setLogoUrl(logoUrl);
Mockito.when(brandingRepo.findTopByOrderByIdBrandingAsc())
.thenReturn(java.util.Optional.of(branding));
}

    /** Rellena como lo hace el envío real, con la plantilla de la que salen sus variables. */
    private String fillConPlantilla(String text, Map<String, Object> variables,
                                    com.mesaayuda.entity.NotificationTemplate template) throws Exception {
        java.lang.reflect.Method m = NotificationService.class
                .getDeclaredMethod("fill", String.class, Map.class,
                        com.mesaayuda.entity.NotificationTemplate.class);
        m.setAccessible(true);
        return (String) m.invoke(service, text, variables, template);
    }

    private com.mesaayuda.entity.NotificationTemplate plantilla(String nombre, String imagen) {
        com.mesaayuda.entity.NotificationTemplate t = new com.mesaayuda.entity.NotificationTemplate();
        t.setNameTemplate(nombre);
        t.setImageUrl(imagen);
        return t;
    }

    private Map<String, Object> comunes(Map<String, Object> variables) throws Exception {
        java.lang.reflect.Method m = NotificationService.class
                .getDeclaredMethod("comunes", Map.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> resultado = (Map<String, Object>) m.invoke(service, variables);
        return resultado;
    }

    private String resumenEnApp(String type, com.mesaayuda.entity.Ticket ticket, String subject)
            throws Exception {
        java.lang.reflect.Method m = NotificationService.class
                .getDeclaredMethod("resumenEnApp", String.class, com.mesaayuda.entity.Ticket.class, String.class);
        m.setAccessible(true);
        return (String) m.invoke(service, type, ticket, subject);
    }

    private com.mesaayuda.entity.Ticket ticket(UUID id, String titulo) {
        com.mesaayuda.entity.Ticket t = new com.mesaayuda.entity.Ticket();
        t.setIdTicket(id);
        // id(n) es 00000000-0000-0000-0000-…n: el número visible es ese n.
        t.setNumberTicket(id.getLeastSignificantBits());
        t.setTitleTicket(titulo);
        return t;
    }

    /**
     * Los enlaces del pie de las 14 plantillas usan [sitioWeb] y [soporte], y los
     * botones CTA recién restaurados usan [urlTicket] y compañía. Ninguno de esos
     * marcadores estaba en el conjunto común, así que fill() los vaciaba y los
     * correos salían con href="" y botones que no llevaban a ninguna parte.
     */
    @Test
    void losEnlacesDelPieYCtaSeRellenanConLaBaseDelFrontend() throws Exception {
        ReflectionTestUtils.setField(service, "frontendBaseUrl", "https://mesa.coacmagisterio.fin.ec");
        Map<String, Object> vars = comunes(Map.of("id", 42, "ticketId", id(42), "titulo", "Impresora rota"));

        assertEquals("https://mesa.coacmagisterio.fin.ec", vars.get("sitioWeb"));
        assertEquals("https://mesa.coacmagisterio.fin.ec", vars.get("soporte"));
        assertEquals("https://mesa.coacmagisterio.fin.ec/tickets/" + id(42), vars.get("urlTicket"));
        assertEquals("https://mesa.coacmagisterio.fin.ec/tickets/" + id(42) + "?calificar=1", vars.get("urlCalificar"));
        assertEquals("https://mesa.coacmagisterio.fin.ec/auth/login", vars.get("urlLogin"));
        assertEquals("https://mesa.coacmagisterio.fin.ec/tickets/new", vars.get("urlNuevoTicket"));
        assertEquals("https://mesa.coacmagisterio.fin.ec/admin/sla-config", vars.get("urlSlaConfig"));
    }

    /** Un evento sin ticket (evaluación mensual) no debe inventar un enlace. */
    @Test
    void sinTicketNoSeFabricaUnEnlaceATicket() throws Exception {
        ReflectionTestUtils.setField(service, "frontendBaseUrl", "https://mesa.coacmagisterio.fin.ec");
        Map<String, Object> vars = comunes(Map.of());

        assertEquals("", vars.get("urlTicket"));
        assertEquals("", vars.get("urlCalificar"));
        assertEquals("https://mesa.coacmagisterio.fin.ec/dashboard", vars.get("urlDashboard"));
    }

    /** notifySimple() pasa id 0 cuando no hay ticket; tampoco debe enlazar. */
    @Test
    void elTicketCeroSeTrataComoAusente() throws Exception {
        ReflectionTestUtils.setField(service, "frontendBaseUrl", "https://mesa.coacmagisterio.fin.ec");
        Map<String, Object> vars = comunes(Map.of("id", 0));

        assertEquals("", vars.get("urlTicket"));
        assertEquals("", vars.get("urlCalificar"));
    }

    /** Una barra final de más no debe producir una doble barra en la URL. */
    @Test
    void laBarraFinalSobranteNoSeDuplica() throws Exception {
        ReflectionTestUtils.setField(service, "frontendBaseUrl", "https://mesa.coacmagisterio.fin.ec/");
        Map<String, Object> vars = comunes(Map.of("id", 7, "ticketId", id(7)));

        assertEquals("https://mesa.coacmagisterio.fin.ec", vars.get("sitioWeb"));
        assertEquals("https://mesa.coacmagisterio.fin.ec/tickets/" + id(7), vars.get("urlTicket"));
    }

    /**
     * Sin app.frontend-base-url los enlaces salen vacíos a propósito. Es preferible
     * un botón sin destino a uno que apunte a la API, que no es navegable.
     */
    @Test
    void sinBaseDelFrontendLosEnlacesQuedanVacios() throws Exception {
        ReflectionTestUtils.setField(service, "frontendBaseUrl", "");
        Map<String, Object> vars = comunes(Map.of("id", 42, "ticketId", id(42)));

        assertEquals("", vars.get("sitioWeb"));
        assertEquals("", vars.get("urlTicket"));
        assertEquals("", vars.get("urlLogin"));
    }

    /** Un evento propio puede querer pisar una de estas variables. */
    @Test
    void elEventoMandaSobreLaVariableDeEnlace() throws Exception {
        ReflectionTestUtils.setField(service, "frontendBaseUrl", "https://mesa.coacmagisterio.fin.ec");
        // El evento gana porque fill() vuelca sus variables encima de las comunes.
        String salida = fill("[urlTicket] y [sitioWeb]",
                Map.of("id", 5, "urlTicket", "https://otro.sitio/tickets/5"));

        assertEquals("https://otro.sitio/tickets/5 y https://mesa.coacmagisterio.fin.ec", salida);
    }

    /** El botón CTA de una plantilla de ticket ya no sale con href vacío. */
    @Test
    void elBotonCtaDeUnaPlantillaQuedaConDestino() throws Exception {
        ReflectionTestUtils.setField(service, "frontendBaseUrl", "https://mesa.coacmagisterio.fin.ec");
        String html = "<a href=\"[urlTicket]\">Ver ticket #{{id}}</a>";

        // fill() es quien mete las comunes, así que se le pasan las del evento.
        String salida = fill(html, Map.of("id", 11, "ticketId", id(11), "titulo", "Prueba"));

        assertEquals("<a href=\"https://mesa.coacmagisterio.fin.ec/tickets/" + id(11) + "\">Ver ticket #11</a>", salida);
    }

    /** Un evento sin ticket no debe generar un enlace con un id inventado. */
    @Test
    void unaPlantillaDeEvaluacionNoDejaElBotonRoto() throws Exception {
        ReflectionTestUtils.setField(service, "frontendBaseUrl", "https://mesa.coacmagisterio.fin.ec");

        String salida = fill("href=\"[urlTicket]\" y \"[urlDashboard]\"", Map.of());

        assertEquals("href=\"\" y \"https://mesa.coacmagisterio.fin.ec/dashboard\"", salida);
    }

    /**
     * La notificación de la campana guardaba plainText(body), y body es el correo
     * entero: el desplegable mostraba los 1.500 caracteres con el pie y el aviso
     * legal. Ahora guarda una línea por tipo de evento.
     */
    @Test
    void laNotificacionInternaGuardaUnResumenYNoElCorreo() throws Exception {
        assertEquals("Tu ticket 4 \"Impresora rota\" fue resuelto.",
                resumenEnApp("TICKET_RESUELTO", ticket(id(4), "Impresora rota"), "Ticket resuelto"));
        assertEquals("Se registró tu ticket 9 \"Alta de usuario\".",
                resumenEnApp("TICKET_CREADO", ticket(id(9), "Alta de usuario"), "Ticket creado"));
        assertEquals("El ticket 3 \"Corte de red\" incumplió su SLA.",
                resumenEnApp("SLA_BREACHED", ticket(id(3), "Corte de red"), "SLA"));
    }

    /** Sin ticket los resúmenes no deben dejar huecos raros. */
    @Test
    void elResumenQuedaLimpioCuandoNoHayTicket() throws Exception {
        assertEquals("Tu evaluación mensual ya está disponible.",
                resumenEnApp("MONTHLY_EVALUATION", null, "Evaluación mensual disponible"));
        assertEquals("Se te asignó una tarea.",
                resumenEnApp("TASK_ASIGNADA", null, "Tarea asignada"));
    }

    /** Un tipo sin resumen propio cae al asunto, que siempre es corto. */
    @Test
    void unTipoSinResumenCaeAlAsunto() throws Exception {
        assertEquals("Ana escribió en el chat",
                resumenEnApp("CHAT_MENSAJE", ticket(id(2), "Consulta"), "Ana escribió en el chat"));
    }

    /** Un ticket sin título tampoco debe dejar comillas colgando. */
    @Test
    void elResumenOmiteElTituloSiNoHay() throws Exception {
        assertEquals("Tu ticket 5 fue resuelto.",
                resumenEnApp("TICKET_RESUELTO", ticket(id(5), null), "Resuelto"));
    }

    @Test
    void reemplazaLaNotacionConCorchetes() throws Exception {
        assertEquals("Ticket 42: Impresora rota",
                fill("Ticket [id]: [titulo]", Map.of("id", 42, "ticketId", id(42), "titulo", "Impresora rota")));
    }

    @Test
    void reemplazaLaNotacionConLlavesDoble() throws Exception {
        assertEquals("Ticket 42: Impresora rota",
                fill("Ticket {{id}}: {{titulo}}", Map.of("id", 42, "ticketId", id(42), "titulo", "Impresora rota")));
    }

    @Test
    void aceptaLasDosNotacionesEnLaMismaPlantilla() throws Exception {
        String texto = "Ticket {{id}} asignado a [nombre] el [fecha]";
        String salida = fill(texto, Map.of("id", 7, "ticketId", id(7), "nombre", "nicoleo", "fecha", "28/09/2026"));

        assertEquals("Ticket 7 asignado a nicoleo el 28/09/2026", salida);
    }

    /**
     * Si un valor contiene el nombre de otra variable, no debe volver a sustituirse.
     * Antes el orden de iteración de Map.of() era aleatorio y el resultado cambiaba
     * entre arranques.
     */
    @Test
    void noVuelveASustituirUnValorYaResuelto() throws Exception {
        String salida = fill("[saludo] y [nombre]",
                Map.of("saludo", "Hola [nombre]", "nombre", "Ana"));

        assertEquals("Hola [nombre] y Ana", salida);
    }

    @Test
    void unaVariableDesconocidaQuedaVacia() throws Exception {
        assertEquals("Ticket 1 ", fill("Ticket [id] [noExiste]", Map.of("id", 1)));
    }

    @Test
    void unValorVacioNoRompeLaPlantilla() throws Exception {
        assertEquals("a  b", fill("a [x] b", Map.of("x", "")));
    }

    /**
     * Las plantillas de TICKET_CREADO / TICKET_ASIGNADO piden solicitante, area,
     * categoria, prioridad, estado y fecha. El disparador solo pasaba id y titulo,
     * así que esos campos salían en blanco aunque los marcadores ya se reemplazaran.
     */
    @Test
    void losDisparadoresDeTicketAportanTodasLasVariablesDeLaPlantilla() throws Exception {
        com.mesaayuda.entity.Ticket ticket = new com.mesaayuda.entity.Ticket();
        ticket.setIdTicket(id(8));
        ticket.setTitleTicket("Verificacion final de variables");
        ticket.setDescriptionTicket("Confirma los datos del ticket");
        ticket.setUserTicket(usuario("Solicitante", "solicitante@coacmagisterio.fin.ec"));
        ticket.setAgentTicket(usuario("Agente", "agente@coacmagisterio.fin.ec"));
        ticket.setAreaTicket(texto(com.mesaayuda.entity.Area.class, "nameArea", "TI"));
        ticket.setCategoryTicket(texto(com.mesaayuda.entity.Category.class, "nameCategory", "Soporte Tecnico"));
        ticket.setPriorityTicket(texto(com.mesaayuda.entity.Priority.class, "namePriority", "MEDIA"));
        ticket.setStatusTicket(texto(com.mesaayuda.entity.Status.class, "nameStatus", "ASIGNADO"));
        ticket.setCreatedAtTicket(java.time.OffsetDateTime.of(2026, 9, 28, 10, 0, 0, 0, java.time.ZoneOffset.UTC));

        java.lang.reflect.Method m = NotificationService.class
                .getDeclaredMethod("ticketVars", com.mesaayuda.entity.Ticket.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> vars = (Map<String, Object>) m.invoke(service, ticket);

        // solicitante y agente van con el nombre de la persona, no con el correo: es lo
        // que se pide en los correos ("Estimado/a Juan Pérez"). Antes esta
        // aserción fijaba el correo, que es justo lo que había que corregir.
        assertEquals("Solicitante Prueba", vars.get("solicitante"));
        assertEquals("Agente Prueba", vars.get("agente"));
        assertEquals("TI", vars.get("area"));
        assertEquals("Soporte Tecnico", vars.get("categoria"));
        assertEquals("MEDIA", vars.get("prioridad"));
        assertEquals("MEDIA", vars.get("priority"));
        assertEquals("ASIGNADO", vars.get("estado"));
        assertEquals("ASIGNADO", vars.get("status"));
        assertEquals("2026-09-28", vars.get("fechaAsignacion"));
        assertEquals("Verificacion final de variables", vars.get("titulo"));
    }

    /** Un ticket sin area, categoria ni agente tampoco debe lanzar excepcion. */
    @Test
    void unTicketSinRelacionesDevuelveCadenasVacias() throws Exception {
        com.mesaayuda.entity.Ticket ticket = new com.mesaayuda.entity.Ticket();
        ticket.setIdTicket(id(1));
        ticket.setTitleTicket("Sin datos");

        java.lang.reflect.Method m = NotificationService.class
                .getDeclaredMethod("ticketVars", com.mesaayuda.entity.Ticket.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> vars = (Map<String, Object>) m.invoke(service, ticket);

        assertEquals("", vars.get("area"));
        assertEquals("", vars.get("categoria"));
        assertEquals("", vars.get("estado"));
        assertEquals("", vars.get("solicitante"));
        assertEquals("", vars.get("agente"));
    }

    private com.mesaayuda.entity.User usuario(String nombre, String correo) {
        com.mesaayuda.entity.User u = new com.mesaayuda.entity.User();
        u.setFirstName(nombre);
        u.setLastName("Prueba");
        u.setCorporateEmailUser(correo);
        return u;
    }

    private <T> T texto(Class<T> tipo, String campo, String valor) throws Exception {
        T instancia = tipo.getDeclaredConstructor().newInstance();
        tipo.getMethod("set" + campo.substring(0, 1).toUpperCase() + campo.substring(1), String.class)
                .invoke(instancia, valor);
        return instancia;
    }

    /**
     * El nombre de destino tiene que caer en una cadena cuando la persona no
     * tiene nada de nombre: primero el nombre de Outlook, luego nombre y
     * apellidos, luego la parte del correo anterior a la arroba.
     */
    @Test
    void elNombreVisiblePrefiereOutlookYTerminaEnElCorreo() {
        com.mesaayuda.entity.User conOutlook = new com.mesaayuda.entity.User();
        conOutlook.setDisplayName("Ana Pérez");
        conOutlook.setFirstName("Otro");
        conOutlook.setCorporateEmailUser("correo@coacmagisterio.fin.ec");
        assertEquals("Ana Pérez", com.mesaayuda.service.NombreVisible.de(conOutlook));

        com.mesaayuda.entity.User conNombres = new com.mesaayuda.entity.User();
        conNombres.setFirstName("Luis");
        conNombres.setLastName("Ruiz");
        conNombres.setCorporateEmailUser("correo@coacmagisterio.fin.ec");
        assertEquals("Luis Ruiz", com.mesaayuda.service.NombreVisible.de(conNombres));

        com.mesaayuda.entity.User soloCorreo = new com.mesaayuda.entity.User();
        soloCorreo.setCorporateEmailUser("jzamora@coacmagisterio.fin.ec");
        assertEquals("jzamora", com.mesaayuda.service.NombreVisible.de(soloCorreo));

        assertEquals("", com.mesaayuda.service.NombreVisible.de(null));
    }
}
