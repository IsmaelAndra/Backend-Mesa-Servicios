package com.mesaayuda.service;

import com.mesaayuda.entity.MicrosoftGraphConfig;
import com.mesaayuda.exception.BadRequestException;
import com.mesaayuda.repository.MicrosoftGraphConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;

/**
 * Obtiene y administra el access token de Microsoft Graph.
 *
 * <p>Reutiliza el token mientras siga vigente (cache en memoria) y solo solicita
 * uno nuevo cuando expira. Nunca persiste el token ni lo escribe en logs.</p>
 */
@Service
public class MicrosoftGraphTokenService {

    private static final Logger log = LoggerFactory.getLogger(MicrosoftGraphTokenService.class);

    @Autowired
    private MicrosoftGraphConfigRepository configRepository;

    @Autowired
    private SecretCryptoService cryptoService;

    private final RestTemplate restTemplate = GraphHttpClient.create();

    private volatile String cachedToken;
    private volatile Instant cachedUntil = Instant.EPOCH;

    public synchronized String getAccessToken() {
        if (cachedToken != null && Instant.now().isBefore(cachedUntil.minusSeconds(60))) {
            return cachedToken;
        }
        MicrosoftGraphConfig config = configRepository.findFirstByOrderByIdGraphConfigAsc()
                .orElseThrow(() -> new BadRequestException("No hay configuración de Microsoft Graph"));
        requireComplete(config);

        String token = requestToken(config);
        this.cachedToken = token;
        return token;
    }

    /** Invalida la cache (por ejemplo al cambiar la configuración). */
    public synchronized void invalidate() {
        cachedToken = null;
        cachedUntil = Instant.EPOCH;
    }

    private String requestToken(MicrosoftGraphConfig config) {
        String secret = cryptoService.decrypt(config.getClientSecretEncrypted());
        if (secret == null || secret.isBlank()) {
            throw new BadRequestException("El client_secret no está configurado");
        }

        String url = "https://login.microsoftonline.com/" + config.getTenantId().trim()
                + "/oauth2/v2.0/token";

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", config.getClientId().trim());
        form.add("client_secret", secret);
        form.add("scope", config.getScope() != null && !config.getScope().isBlank()
                ? config.getScope().trim() : "https://graph.microsoft.com/.default");
        form.add("grant_type", config.getGrantType() != null && !config.getGrantType().isBlank()
                ? config.getGrantType().trim() : "client_credentials");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        this.cachedUntil = Instant.now().plusSeconds(3600);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                    url, new HttpEntity<>(form, headers), Map.class);
            if (response == null || response.get("access_token") == null) {
                throw new BadRequestException("Microsoft no devolvió un access token");
            }
            Object expires = response.get("expires_in");
            if (expires != null) {
                long seconds = parseSeconds(expires);
                if (seconds > 0) {
                    this.cachedUntil = Instant.now().plusSeconds(seconds);
                }
            }
            return response.get("access_token").toString();
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            // No se registra el secreto ni el token, solo el motivo.
            log.warn("Fallo al solicitar token a Microsoft Graph: {}", rootMessage(e));
            throw new BadRequestException("No se pudo autenticar con Microsoft Graph: " + rootMessage(e));
        }
    }

    private long parseSeconds(Object value) {
        try {
            return (long) Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String rootMessage(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
    }

    private void requireComplete(MicrosoftGraphConfig config) {
        if (config.getTenantId() == null || config.getTenantId().isBlank()
                || config.getClientId() == null || config.getClientId().isBlank()
                || config.getClientSecretEncrypted() == null || config.getClientSecretEncrypted().isBlank()) {
            throw new BadRequestException("La configuración de Microsoft Graph está incompleta");
        }
    }
}
