package com.mesaayuda.proyectos.chat;

import com.mesaayuda.entity.User;
import com.mesaayuda.exception.BadRequestException;
import com.mesaayuda.proyectos.Proyecto;
import com.mesaayuda.proyectos.ProyectoService;
import com.mesaayuda.repository.UserRepository;
import com.mesaayuda.service.SseEventBroker;
import com.mesaayuda.service.TicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.mesaayuda.IdsPrueba.id;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProyectoChatServiceTest {

    private static final UUID PROYECTO = id(50);

    private final ProyectoService proyectos = mock(ProyectoService.class);
    private final ProyectoMensajeRepository mensajes = mock(ProyectoMensajeRepository.class);
    private final ProyectoAdjuntoRepository adjuntos = mock(ProyectoAdjuntoRepository.class);
    private final UserRepository usuarios = mock(UserRepository.class);
    private final TicketService tickets = mock(TicketService.class);
    private final SseEventBroker sse = mock(SseEventBroker.class);
    private final com.mesaayuda.service.NotificationService avisos = mock(com.mesaayuda.service.NotificationService.class);
    private ProyectoChatService chat;
    private User yo;

    @BeforeEach
    void setUp() {
        chat = new ProyectoChatService(proyectos, mensajes, adjuntos, usuarios, tickets, sse, avisos);
        yo = new User();
        yo.setIdUser(id(1));
        yo.setCorporateEmailUser("yo@coac");
        when(mensajes.save(any())).thenAnswer(inv -> {
            ProyectoMensaje m = inv.getArgument(0);
            m.setIdMensaje(id(77));
            return m;
        });
        when(usuarios.findAllById(any())).thenReturn(List.of(yo));
    }

    private void acceso(boolean escribe) {
        Proyecto p = new Proyecto();
        p.setIdProyecto(PROYECTO);
        when(proyectos.accesoChat(PROYECTO)).thenReturn(new ProyectoService.AccesoChat(yo, p, escribe));
    }

    @Test
    void quienSoloLeeNoEscribe() {
        acceso(false);
        assertThrows(AccessDeniedException.class, () -> chat.enviar(PROYECTO, "hola", List.of()));
        verify(mensajes, never()).save(any());
    }

    @Test
    void mensajeVacioSinArchivosSeRechaza() {
        acceso(true);
        assertThrows(BadRequestException.class, () -> chat.enviar(PROYECTO, "   ", List.of()));
    }

    @Test
    void masDeCincoArchivosSeRechazaAntesDeGuardar() {
        acceso(true);
        List<org.springframework.web.multipart.MultipartFile> seis = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) seis.add(new MockMultipartFile("archivos", "a" + i + ".txt", "text/plain", "x".getBytes()));
        assertThrows(BadRequestException.class, () -> chat.enviar(PROYECTO, "", seis));
        verify(mensajes, never()).save(any());
    }

    @Test
    void elMensajeLlegaEnVivoAlEquipo() {
        acceso(true);
        when(proyectos.participantes(PROYECTO)).thenReturn(new HashSet<>(Set.of(id(2), id(3))));
        ProyectoChatService.MensajeVista v = chat.enviar(PROYECTO, "Hola equipo", List.of());
        assertEquals("Hola equipo", v.texto());
        verify(mensajes).marcarLeido(PROYECTO, yo.getIdUser());
        verify(sse).publishAUsuarios(eq(Set.of(id(1), id(2), id(3))), eq("proyecto-chat"), any());
        verify(tickets, never()).validarTipoAdjunto(anyString(), any());
    }

    @Test
    void elHtmlSeGuardaSinScriptsNiManejadores() {
        acceso(true);
        when(proyectos.participantes(PROYECTO)).thenReturn(new HashSet<>());
        ProyectoChatService.MensajeVista v = chat.enviar(PROYECTO,
                "<p><b>Hola</b><script>alert(1)</script><img src=x onerror=alert(2)><iframe src=\"x\"></iframe></p>", List.of());
        assertEquals("<p><b>Hola</b><img src=x></p>", v.texto());
    }

    @Test
    void unEditorVacioCuentaComoMensajeVacio() {
        acceso(true);
        assertThrows(BadRequestException.class, () -> chat.enviar(PROYECTO, "<p><br></p>", List.of()));
    }

    @Test
    void laMencionAvisaSoloAParticipantesYNuncaAUnoMismo() {
        acceso(true);
        when(proyectos.participantes(PROYECTO)).thenReturn(new HashSet<>(Set.of(id(2))));
        User otro = new User();
        otro.setIdUser(id(2));
        when(usuarios.findAllById(eq(new java.util.LinkedHashSet<>(List.of(id(2)))))).thenReturn(List.of(otro));
        chat.enviar(PROYECTO, "<p>Hola @Otro</p>", List.of(), List.of(id(2), id(9), id(1)));
        verify(avisos).notifyProyecto(eq(otro), eq("PROYECTO_MENCION"), anyString(), anyString(), anyString());
        verify(avisos, times(1)).notifyProyecto(any(), anyString(), anyString(), anyString(), anyString());
    }

    private ProyectoMensaje mensaje(UUID autor, java.time.OffsetDateTime creado) {
        ProyectoMensaje m = new ProyectoMensaje();
        m.setIdMensaje(id(88));
        m.setProyectoId(PROYECTO);
        m.setAutorId(autor);
        m.setTexto("<p>original</p>");
        m.setCreatedAt(creado);
        return m;
    }

    @Test
    void eliminarDejaElMensajeSinTextoNiArchivosYMarcadoComoEliminado() {
        acceso(true);
        when(proyectos.participantes(PROYECTO)).thenReturn(new HashSet<>());
        when(mensajes.findById(id(88))).thenReturn(java.util.Optional.of(mensaje(id(1), java.time.OffsetDateTime.now())));
        ProyectoChatService.MensajeVista v = chat.eliminar(PROYECTO, id(88));
        assertTrue(v.eliminado());
        assertNull(v.texto());
        assertTrue(v.adjuntos().isEmpty());
    }

    @Test
    void nadieEditaElMensajeDeOtro() {
        acceso(true);
        when(mensajes.findById(id(88))).thenReturn(java.util.Optional.of(mensaje(id(2), java.time.OffsetDateTime.now())));
        assertThrows(AccessDeniedException.class, () -> chat.editar(PROYECTO, id(88), "<p>cambio</p>"));
    }

    @Test
    void pasadosLosQuinceMinutosYaNoSeEdita() {
        acceso(true);
        when(mensajes.findById(id(88))).thenReturn(java.util.Optional.of(mensaje(id(1), java.time.OffsetDateTime.now().minusMinutes(20))));
        assertThrows(BadRequestException.class, () -> chat.editar(PROYECTO, id(88), "<p>cambio</p>"));
    }

    @Test
    void editarMarcaLaHoraDeEdicion() {
        acceso(true);
        when(proyectos.participantes(PROYECTO)).thenReturn(new HashSet<>());
        when(mensajes.findById(id(88))).thenReturn(java.util.Optional.of(mensaje(id(1), java.time.OffsetDateTime.now())));
        ProyectoChatService.MensajeVista v = chat.editar(PROYECTO, id(88), "<p>corregido</p>");
        assertEquals("<p>corregido</p>", v.texto());
        assertNotNull(v.editado());
    }
}
