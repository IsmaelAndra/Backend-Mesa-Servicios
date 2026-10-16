package com.mesaayuda.service;

import com.mesaayuda.entity.NotificationSound;
import com.mesaayuda.exception.BadRequestException;
import com.mesaayuda.repository.NotificationSoundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationSoundServiceTest {

    @Mock private NotificationSoundRepository repository;
    @Mock private UserService userService;

    private NotificationSoundService service;

    @BeforeEach
    void setUp() {
        service = new NotificationSoundService(repository, userService);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repository.findByScopeSoundAndKeySound(any(), any())).thenReturn(Optional.empty());
    }

    private static byte[] mp3() {
        byte[] b = new byte[64];
        b[0] = 'I'; b[1] = 'D'; b[2] = '3';
        return b;
    }

    @Test
    void reconoceLosAudiosPorSuContenido() {
        assertEquals("audio/mpeg", NotificationSoundService.tipoDeAudio(mp3()));
        byte[] wav = "RIFF0000WAVEfmt ".getBytes();
        assertEquals("audio/wav", NotificationSoundService.tipoDeAudio(wav));
        byte[] ogg = "OggS000000000000".getBytes();
        assertEquals("audio/ogg", NotificationSoundService.tipoDeAudio(ogg));
        // Un HTML renombrado a .mp3 no pasa.
        assertNull(NotificationSoundService.tipoDeAudio("<html><script>x</script>".getBytes()));
    }

    @Test
    void guardaElAudioGeneralDeUnaUrgencia() {
        var r = service.subirAudio("URGENCIA", "urgent",
                new MockMultipartFile("file", "alarma.mp3", "audio/mpeg", mp3()));

        assertEquals("URGENCIA", r.scope());
        assertEquals("urgent", r.key());
        assertTrue(r.hasAudio());
        assertEquals("alarma.mp3", r.fileName());
    }

    @Test
    void rechazaUnArchivoQueNoEsAudioOMayorDe1MB() {
        assertThrows(BadRequestException.class, () -> service.subirAudio("TIPO", "SLA_BREACHED",
                new MockMultipartFile("file", "x.mp3", "audio/mpeg", "no soy audio, solo texto".getBytes())));
        byte[] grande = new byte[NotificationSoundService.MAX_AUDIO_BYTES + 1];
        grande[0] = 'I'; grande[1] = 'D'; grande[2] = '3';
        assertThrows(BadRequestException.class, () -> service.subirAudio("TIPO", "SLA_BREACHED",
                new MockMultipartFile("file", "x.mp3", "audio/mpeg", grande)));
    }

    @Test
    void validaAmbitoClaveYUrgencia() {
        assertThrows(BadRequestException.class, () -> service.restablecer("OTRO", "urgent"));
        assertThrows(BadRequestException.class, () -> service.restablecer("URGENCIA", "altisima"));
        assertThrows(BadRequestException.class, () -> service.cambiarUrgencia("TIPO", "../etc", "urgent"));
        assertThrows(BadRequestException.class, () -> service.cambiarUrgencia("TIPO", "SLA_WARNING", "fuerte"));
        // La urgencia es cosa de los tipos: una urgencia no tiene "otra urgencia".
        assertThrows(BadRequestException.class, () -> service.cambiarUrgencia("URGENCIA", "soft", "urgent"));
    }

    @Test
    void elSonidoDeTemporadaVaPorTemaYUrgencia() {
        var r = service.subirAudio("TEMPORADA", "navidad:URGENT",
                new MockMultipartFile("file", "campanas.mp3", "audio/mpeg", mp3()));

        assertEquals("TEMPORADA", r.scope());
        assertEquals("NAVIDAD:urgent", r.key());
        assertThrows(BadRequestException.class, () -> service.restablecer("TEMPORADA", "NAVIDAD"));
        assertThrows(BadRequestException.class, () -> service.restablecer("TEMPORADA", "NAVIDAD:fuerte"));
    }

    @Test
    void quitarElAudioDeUnTipoSinUrgenciaPropiaBorraLaFila() {
        NotificationSound s = new NotificationSound();
        s.setScopeSound("TIPO");
        s.setKeySound("CHAT_MENSAJE");
        s.setAudioSound(mp3());
        when(repository.findByScopeSoundAndKeySound("TIPO", "CHAT_MENSAJE")).thenReturn(Optional.of(s));

        service.quitarAudio("TIPO", "chat_mensaje");

        verify(repository).delete(s);
    }
}
