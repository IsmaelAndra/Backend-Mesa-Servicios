package com.mesaayuda.service;

import com.mesaayuda.entity.MicrosoftGraphConfig;
import com.mesaayuda.entity.User;
import com.mesaayuda.repository.MicrosoftGraphConfigRepository;
import com.mesaayuda.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Sincroniza los datos básicos de Microsoft 365 hacia la entidad {@link User}.
 * Evita consultar Graph en cada inicio de sesión: solo sincroniza cuando el
 * usuario aún no tiene datos o cuando su último sync excede el intervalo.
 */
@Service
public class MicrosoftGraphUserSyncService {

    private static final Logger log = LoggerFactory.getLogger(MicrosoftGraphUserSyncService.class);

    @Autowired
    private MicrosoftGraphService graphService;

    @Autowired
    private MicrosoftGraphConfigRepository configRepository;

    @Autowired
    private UserRepository userRepository;

    @Value("${app.uploads.dir}")
    private String uploadDir;

    /** Se invoca desde el login; no bloquea ni rompe la autenticación. */
    @Async
    public void syncOnLogin(Long userId) {
        try {
            syncUser(userId, true);
        } catch (Exception e) {
            log.warn("No se pudo sincronizar el usuario {} con Microsoft Graph: {}", userId, e.getMessage());
        }
    }

    /** Sincroniza a todos los usuarios activos. Devuelve cuántos se actualizaron. */
    public int syncAll() {
        MicrosoftGraphConfig config = enabledConfigOrNull();
        if (config == null) {
            return 0;
        }
        int count = 0;
        for (User user : userRepository.findByStatusUserAndDeletedUserFalse("ACTIVE")) {
            if (!isStale(user, config.getSyncIntervalMinutes())) {
                continue;
            }
            try {
                if (syncUser(user.getIdUser(), false) > 0) {
                    count++;
                }
            } catch (Exception e) {
                log.warn("Sincronización fallida para {}: {}", user.getCorporateEmailUser(), e.getMessage());
            }
        }
        if (config != null) {
            config.setLastSyncAt(OffsetDateTime.now());
            configRepository.save(config);
        }
        return count;
    }

    /**
     * @param force si true, ignora el intervalo y sincroniza (usado en el primer login).
     * @return 1 si se actualizó el usuario, 0 si no aplicó.
     */
    @Transactional
    public int syncUser(Long userId, boolean force) {
        MicrosoftGraphConfig config = enabledConfigOrNull();
        if (config == null) {
            return 0;
        }
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return 0;
        }
        if (!force && !isStale(user, config.getSyncIntervalMinutes())) {
            return 0;
        }

        MicrosoftGraphService.GraphUser info = graphService.getUser(user.getCorporateEmailUser());
        if (info == null) {
            // El usuario no existe en Microsoft 365: no alteramos datos locales.
            user.setMicrosoftSynced(false);
            user.setLastSync(OffsetDateTime.now());
            userRepository.save(user);
            return 0;
        }

        if (info.displayName() != null) user.setDisplayName(info.displayName());
        if (info.givenName() != null) user.setFirstName(info.givenName());
        if (info.surname() != null) user.setLastName(info.surname());
        if (info.userPrincipalName() != null) user.setUserPrincipalName(info.userPrincipalName());
        if (info.accountEnabled() != null) user.setMicrosoftActive(info.accountEnabled());

        try {
            syncPhoto(user, info.userPrincipalName() != null ? info.userPrincipalName()
                    : user.getCorporateEmailUser());
        } catch (Exception e) {
            log.warn("No se pudo sincronizar la foto de {}: {}", user.getCorporateEmailUser(), e.getMessage());
        }

        user.setMicrosoftSynced(true);
        user.setLastSync(OffsetDateTime.now());
        userRepository.save(user);
        return 1;
    }

    private void syncPhoto(User user, String upn) {
        MicrosoftGraphService.GraphPhoto photo = graphService.getPhoto(upn, user.getMicrosoftPhotoEtag());
        if (photo == null) {
            return; // sin foto en Microsoft 365
        }
        if (photo.notModified()) {
            return; // no descargar si no cambió
        }
        byte[] content = photo.content();
        if (content == null || content.length == 0) {
            return;
        }
        try {
            Path dir = Paths.get(uploadDir, "avatars").toAbsolutePath().normalize();
            Files.createDirectories(dir);
            String ext = detectExtension(content);
            String fileName = "user_" + user.getIdUser() + "." + ext;
            Path target = dir.resolve(fileName);
            Files.copy(new java.io.ByteArrayInputStream(content), target, StandardCopyOption.REPLACE_EXISTING);

            user.setPhotoUrl("/uploads/avatars/" + fileName);
            user.setMicrosoftPhotoEtag(photo.eTag());
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo guardar la foto: " + e.getMessage(), e);
        }
    }

    private String detectExtension(byte[] content) {
        if (content.length >= 8
                && (content[0] & 0xFF) == 0x89 && content[1] == 'P' && content[2] == 'N' && content[3] == 'G') {
            return "png";
        }
        if (content.length >= 3
                && (content[0] & 0xFF) == 0xFF && (content[1] & 0xFF) == 0xD8) {
            return "jpg";
        }
        return "jpg";
    }

    private boolean isStale(User user, Integer intervalMinutes) {
        if (user.getLastSync() == null || !Boolean.TRUE.equals(user.getMicrosoftSynced())) {
            return true;
        }
        int minutes = intervalMinutes == null || intervalMinutes <= 0 ? 1440 : intervalMinutes;
        return user.getLastSync().plusMinutes(minutes).isBefore(OffsetDateTime.now());
    }

    private MicrosoftGraphConfig enabledConfigOrNull() {
        MicrosoftGraphConfig config = configRepository.findFirstByOrderByIdGraphConfigAsc().orElse(null);
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            return null;
        }
        if (config.getTenantId() == null || config.getTenantId().isBlank()
                || config.getClientId() == null || config.getClientId().isBlank()
                || config.getClientSecretEncrypted() == null || config.getClientSecretEncrypted().isBlank()) {
            return null;
        }
        return config;
    }
}
