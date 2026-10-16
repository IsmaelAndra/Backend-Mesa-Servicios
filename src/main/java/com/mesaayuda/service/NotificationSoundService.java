package com.mesaayuda.service;

import com.mesaayuda.dto.NotificationSoundResponse;
import com.mesaayuda.entity.NotificationSound;
import com.mesaayuda.exception.BadRequestException;
import com.mesaayuda.exception.ResourceNotFoundException;
import com.mesaayuda.repository.NotificationSoundRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Sonidos de notificación configurados por el SUPER_ADMIN para todos.
 *
 * <p>Antes vivían en el localStorage del navegador del administrador: solo le
 * sonaban a él y un MP3 de 1 MB llenaba el almacenamiento del navegador.
 */
@Service
public class NotificationSoundService {

    /** Tope de un audio propio: un aviso es corto y se descarga al iniciar sesión. */
    public static final int MAX_AUDIO_BYTES = 1_000_000;

    private static final Set<String> URGENCIAS = Set.of("urgent", "normal", "soft");
    private static final Pattern TIPO = Pattern.compile("^[A-Z][A-Z0-9_]{1,59}$");

    private final NotificationSoundRepository repository;
    private final UserService userService;

    public NotificationSoundService(NotificationSoundRepository repository, UserService userService) {
        this.repository = repository;
        this.userService = userService;
    }

    @Transactional(readOnly = true)
    public List<NotificationSoundResponse> listar() {
        return repository.findAll().stream()
                .sorted(Comparator.comparing(NotificationSound::getScopeSound)
                        .thenComparing(NotificationSound::getKeySound))
                .map(NotificationSoundService::aRespuesta)
                .toList();
    }

    @Transactional(readOnly = true)
    public NotificationSound audio(String scope, String key) {
        NotificationSound s = repository.findByScopeSoundAndKeySound(ambito(scope), clave(scope, key))
                .orElseThrow(() -> new ResourceNotFoundException("Sonido", "clave", key));
        if (s.getAudioSound() == null || s.getAudioSound().length == 0) {
            throw new ResourceNotFoundException("Sonido", "clave", key);
        }
        return s;
    }

    /** Urgencia de un tipo (solo ámbito TIPO). null la devuelve a la de siempre. */
    @Transactional
    public NotificationSoundResponse cambiarUrgencia(String scope, String key, String urgencia) {
        String ambito = ambito(scope);
        if (!NotificationSound.AMBITO_TIPO.equals(ambito)) {
            throw new BadRequestException("La urgencia solo se cambia en un tipo de notificación.");
        }
        if (urgencia != null && !urgencia.isBlank() && !URGENCIAS.contains(urgencia)) {
            throw new BadRequestException("Urgencia no válida: " + urgencia);
        }
        NotificationSound s = obtenerOCrear(ambito, clave(scope, key));
        s.setUrgencySound(urgencia == null || urgencia.isBlank() ? null : urgencia);
        return guardar(s);
    }

    @Transactional
    public NotificationSoundResponse subirAudio(String scope, String key, MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new BadRequestException("Seleccione un archivo de audio.");
        }
        if (archivo.getSize() > MAX_AUDIO_BYTES) {
            throw new BadRequestException("El audio supera 1 MB: elija un sonido más corto.");
        }
        byte[] contenido;
        try {
            contenido = archivo.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("No se pudo leer el archivo de audio.");
        }
        // El tipo se decide por el contenido, nunca por lo que diga el navegador.
        String mime = tipoDeAudio(contenido);
        if (mime == null) {
            throw new BadRequestException("El archivo no es un audio MP3, WAV u OGG válido.");
        }
        NotificationSound s = obtenerOCrear(ambito(scope), clave(scope, key));
        s.setAudioSound(contenido);
        s.setMimeSound(mime);
        s.setFileNameSound(nombreLimpio(archivo.getOriginalFilename()));
        return guardar(s);
    }

    @Transactional
    public void quitarAudio(String scope, String key) {
        repository.findByScopeSoundAndKeySound(ambito(scope), clave(scope, key)).ifPresent(s -> {
            if (s.getUrgencySound() == null) {
                // Sin audio ni urgencia propia la fila ya no aporta nada.
                repository.delete(s);
                return;
            }
            s.setAudioSound(null);
            s.setMimeSound(null);
            s.setFileNameSound(null);
            guardar(s);
        });
    }

    /** Devuelve un tipo o una urgencia a su sonido de siempre. */
    @Transactional
    public void restablecer(String scope, String key) {
        repository.findByScopeSoundAndKeySound(ambito(scope), clave(scope, key)).ifPresent(repository::delete);
    }

    @Transactional
    public void restablecerTodo() {
        repository.deleteAll();
    }

    // ============================ utilidades ============================

    private NotificationSound obtenerOCrear(String ambito, String clave) {
        return repository.findByScopeSoundAndKeySound(ambito, clave).orElseGet(() -> {
            NotificationSound nuevo = new NotificationSound();
            nuevo.setScopeSound(ambito);
            nuevo.setKeySound(clave);
            nuevo.setCreatedAtSound(OffsetDateTime.now());
            return nuevo;
        });
    }

    private NotificationSoundResponse guardar(NotificationSound s) {
        s.setUpdatedAtSound(OffsetDateTime.now());
        try {
            s.setUpdatedBySound(userService.getCurrentUser().getIdUser());
        } catch (RuntimeException e) {
            s.setUpdatedBySound(null);
        }
        return aRespuesta(repository.save(s));
    }

    private static String ambito(String scope) {
        String a = scope == null ? "" : scope.trim().toUpperCase();
        if (!NotificationSound.AMBITO_URGENCIA.equals(a) && !NotificationSound.AMBITO_TIPO.equals(a)) {
            throw new BadRequestException("Ámbito no válido: use URGENCIA o TIPO.");
        }
        return a;
    }

    private static String clave(String scope, String key) {
        String k = key == null ? "" : key.trim();
        if (NotificationSound.AMBITO_URGENCIA.equals(ambito(scope))) {
            k = k.toLowerCase();
            if (!URGENCIAS.contains(k)) {
                throw new BadRequestException("Urgencia no válida: use urgent, normal o soft.");
            }
            return k;
        }
        k = k.toUpperCase();
        if (!TIPO.matcher(k).matches()) {
            throw new BadRequestException("Tipo de notificación no válido.");
        }
        return k;
    }

    /** MIME según los primeros bytes: MP3 (ID3 o trama), WAV (RIFF/WAVE) u OGG. */
    static String tipoDeAudio(byte[] b) {
        if (b == null || b.length < 12) {
            return null;
        }
        if (b[0] == 'I' && b[1] == 'D' && b[2] == '3') {
            return "audio/mpeg";
        }
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xE0) == 0xE0) {
            return "audio/mpeg";
        }
        if (b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'A' && b[10] == 'V' && b[11] == 'E') {
            return "audio/wav";
        }
        if (b[0] == 'O' && b[1] == 'g' && b[2] == 'g' && b[3] == 'S') {
            return "audio/ogg";
        }
        return null;
    }

    private static String nombreLimpio(String nombre) {
        if (nombre == null) {
            return null;
        }
        String limpio = nombre.replaceAll("[\\\\/]", "_").replaceAll("[\\p{Cntrl}]", "").trim();
        return limpio.length() > 200 ? limpio.substring(0, 200) : limpio;
    }

    private static NotificationSoundResponse aRespuesta(NotificationSound s) {
        return new NotificationSoundResponse(
                s.getScopeSound(), s.getKeySound(), s.getUrgencySound(),
                s.getAudioSound() != null && s.getAudioSound().length > 0,
                s.getFileNameSound(), s.getUpdatedAtSound());
    }
}
