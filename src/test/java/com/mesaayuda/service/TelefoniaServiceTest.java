package com.mesaayuda.service;

import com.mesaayuda.entity.IdsConfiguracion;
import com.mesaayuda.entity.Llamada;
import com.mesaayuda.entity.Role;
import com.mesaayuda.entity.TelefoniaConfig;
import com.mesaayuda.entity.Ticket;
import com.mesaayuda.entity.User;
import com.mesaayuda.exception.BadRequestException;
import com.mesaayuda.repository.LlamadaRepository;
import com.mesaayuda.repository.TelefoniaConfigRepository;
import com.mesaayuda.repository.TicketRepository;
import com.mesaayuda.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static com.mesaayuda.IdsPrueba.id;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Llamadas entre extensiones: quién puede llamar, a qué, la configuración y el canal del puente. */
class TelefoniaServiceTest {

    private static final String TOKEN = "a".repeat(64);

    private LlamadaRepository llamadas;
    private TelefoniaConfigRepository configs;
    private TicketRepository tickets;
    private UserService usuarios;
    private TicketAccessPolicy politica;
    private SecretCryptoService crypto;
    private TelefoniaService servicio;
    private TelefoniaConfig config;
    private User agente;
    private User solicitante;
    private Ticket ticket;

    @BeforeEach
    void setUp() {
        llamadas = mock(LlamadaRepository.class);
        configs = mock(TelefoniaConfigRepository.class);
        tickets = mock(TicketRepository.class);
        usuarios = mock(UserService.class);
        politica = mock(TicketAccessPolicy.class);
        crypto = mock(SecretCryptoService.class);
        servicio = new TelefoniaService(llamadas, configs, tickets, mock(UserRepository.class), usuarios,
                politica, mock(AuditService.class), crypto);

        config = new TelefoniaConfig();
        config.setIdTelefoniaConfig(IdsConfiguracion.TELEFONIA);
        config.setEnabledTelefonia(true);
        config.setUcmUrl("https://192.168.1.235:8089/api");
        config.setUcmUsuario("mesa");
        config.setUcmClaveCifrada("cifrada");
        config.setTokenHash(TelefoniaService.sha256(TOKEN));
        when(configs.findById(IdsConfiguracion.TELEFONIA)).thenReturn(Optional.of(config));
        when(configs.save(any(TelefoniaConfig.class))).thenAnswer(i -> i.getArgument(0));

        agente = persona(id(1), "AGENT", "182");
        solicitante = persona(id(2), "USER", "181");
        ticket = new Ticket();
        ticket.setIdTicket(id(50));
        ticket.setUserTicket(solicitante);
        when(tickets.findById(id(50))).thenReturn(Optional.of(ticket));
        when(usuarios.getCurrentUser()).thenReturn(agente);
        when(politica.esPersonal(agente)).thenReturn(true);
        when(politica.puedeLeer(ticket, agente)).thenReturn(true);
        when(llamadas.save(any(Llamada.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static User persona(java.util.UUID uid, String rol, String extension) {
        Role r = new Role();
        r.setNameRole(rol);
        User u = new User();
        u.setIdUser(uid);
        u.setRolUser(r);
        u.setCorporateEmailUser(rol.toLowerCase() + "@coac.test");
        u.setExtensionUser(extension);
        return u;
    }

    private static TelefoniaService.Latido puente(String token) {
        return new TelefoniaService.Latido(token, "10.0.0.5", "1.1.0", "ok");
    }

    @Test
    void elAgenteLlamaDeSuExtensionALaDelSolicitante() {
        Llamada l = servicio.llamarAlSolicitante(id(50));

        assertEquals("182", l.getCallerExtension());
        assertEquals("181", l.getCalleeExtension());
        assertEquals(Llamada.PENDIENTE, l.getStatusLlamada());
    }

    @Test
    void unSolicitanteNoPuedeLlamar() {
        when(politica.esPersonal(agente)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> servicio.llamarAlSolicitante(id(50)));
        verify(llamadas, never()).save(any());
    }

    @Test
    void sinExtensionDeAlgunaDeLasPartesNoSeLlama() {
        solicitante.setExtensionUser(null);
        assertThrows(BadRequestException.class, () -> servicio.llamarAlSolicitante(id(50)));

        solicitante.setExtensionUser("181");
        agente.setExtensionUser("18a; hangup");
        assertThrows(BadRequestException.class, () -> servicio.llamarAlSolicitante(id(50)),
                "una extension que no son solo digitos no llega a la central");
    }

    @Test
    void dosClicsSeguidosNoGeneranDosLlamadas() {
        Llamada anterior = new Llamada();
        anterior.setCalleeExtension("181");
        anterior.setStatusLlamada(Llamada.PENDIENTE);
        anterior.setCreatedAtLlamada(OffsetDateTime.now().minusSeconds(2));
        when(llamadas.findTopByCallerUserIdOrderByCreatedAtLlamadaDesc(id(1))).thenReturn(anterior);

        assertSame(anterior, servicio.llamarAlSolicitante(id(50)));
        verify(llamadas, never()).save(any());
    }

    @Test
    void apagadaOSinTokenElBotonNoSirve() {
        assertTrue(servicio.habilitada());
        config.setEnabledTelefonia(false);
        assertFalse(servicio.habilitada());
        assertThrows(BadRequestException.class, () -> servicio.llamarAlSolicitante(id(50)));

        config.setEnabledTelefonia(true);
        config.setTokenHash(null);
        assertFalse(servicio.habilitada(), "sin puente autorizado no hay a quien entregar la llamada");
    }

    @Test
    void noSePuedeActivarSinLosDatosDeLaUcm() {
        config.setUcmClaveCifrada(null);
        config.setEnabledTelefonia(false);

        assertThrows(BadRequestException.class,
                () -> servicio.guardar(new TelefoniaService.ConfigRequest(true, null, null, ""), id(1)));
    }

    @Test
    void laClaveEnmascaradaNoSobrescribeLaGuardada() {
        servicio.guardar(new TelefoniaService.ConfigRequest(null, null, null, "********"), id(1));
        assertEquals("cifrada", config.getUcmClaveCifrada());

        when(crypto.encrypt("nueva")).thenReturn("cifrada-nueva");
        servicio.guardar(new TelefoniaService.ConfigRequest(null, null, null, "nueva"), id(1));
        assertEquals("cifrada-nueva", config.getUcmClaveCifrada());
    }

    @Test
    void unaUrlRaraNoSeAcepta() {
        assertThrows(BadRequestException.class, () -> servicio.guardar(
                new TelefoniaService.ConfigRequest(null, "javascript:alert(1)", null, null), id(1)));
    }

    @Test
    void generarTokenInvalidaElAnteriorYSoloGuardaElHash() {
        String nuevo = servicio.generarToken(id(1));

        assertEquals(64, nuevo.length());
        assertEquals(TelefoniaService.sha256(nuevo), config.getTokenHash());
        assertNotEquals(nuevo, config.getTokenHash(), "el token en claro no se guarda");
        assertThrows(AccessDeniedException.class, () -> servicio.recogerPendientes(puente(TOKEN)));
    }

    @Test
    void elPuenteConTokenEquivocadoNoRecibeNada() {
        assertThrows(AccessDeniedException.class, () -> servicio.recogerPendientes(puente("otro")));
        assertThrows(AccessDeniedException.class, () -> servicio.recogerPendientes(puente(null)));
        verify(llamadas, never()).reclamarPendientes(anyInt());
    }

    @Test
    void elPuenteRecogeLasPendientesYQuedaComoVisto() {
        Llamada l = new Llamada();
        l.setStatusLlamada(Llamada.PENDIENTE);
        when(llamadas.reclamarPendientes(anyInt())).thenReturn(List.of(l));
        when(llamadas.saveAll(any())).thenAnswer(i -> i.getArgument(0));

        List<Llamada> lote = servicio.recogerPendientes(puente(TOKEN));

        assertEquals(Llamada.ENVIADA, lote.get(0).getStatusLlamada());
        assertEquals("1.1.0", config.getPuenteVersion());
        assertEquals(Boolean.TRUE, config.getPuenteUcmOk());
    }

    @Test
    void elPuenteRecibeLaClaveDescifrada() {
        when(crypto.decrypt("cifrada")).thenReturn("secreta");

        TelefoniaService.ConfigPuente c = servicio.configParaPuente(puente(TOKEN));

        assertEquals("secreta", c.ucmClave());
        assertEquals("https://192.168.1.235:8089/api", c.ucmUrl());
    }

    @Test
    void elResultadoCierraLaLlamada() {
        Llamada l = new Llamada();
        l.setStatusLlamada(Llamada.ENVIADA);
        when(llamadas.findById(id(9))).thenReturn(Optional.of(l));

        servicio.informarResultado(puente(TOKEN), id(9), false, "status -25");

        assertEquals(Llamada.ERROR, l.getStatusLlamada());
        assertEquals("status -25", l.getErrorLlamada());
    }
}
