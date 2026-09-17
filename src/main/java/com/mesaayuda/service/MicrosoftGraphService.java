package com.mesaayuda.service;

import com.mesaayuda.exception.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cliente HTTP directo contra Microsoft Graph (v1.0).
 * No persiste tokens; delega la obtención en {@link MicrosoftGraphTokenService}.
 */
@Service
public class MicrosoftGraphService {

    private static final Logger log = LoggerFactory.getLogger(MicrosoftGraphService.class);
    private static final String GRAPH_BASE = "https://graph.microsoft.com/v1.0";

    @Autowired
    private MicrosoftGraphTokenService tokenService;

    private final RestTemplate restTemplate = GraphHttpClient.create();

    /** Datos básicos de un usuario obtenidos desde Graph. */
    public record GraphUser(
            String id,
            String displayName,
            String givenName,
            String surname,
            String mail,
            String userPrincipalName,
            Boolean accountEnabled) {
    }

    /** Foto de perfil; si {@code notModified} es true, el contenido no cambió. */
    public record GraphPhoto(byte[] content, String eTag, boolean notModified) {
    }

    public GraphUser getUser(String upnOrEmail) {
        String url = GRAPH_BASE + "/users/" + encode(upnOrEmail)
                + "?$select=id,displayName,givenName,surname,mail,userPrincipalName,accountEnabled";
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, authorizedEntity(null), mapType());
            Map<String, Object> body = response.getBody();
            if (body == null) {
                return null;
            }
            return new GraphUser(
                    asString(body.get("id")),
                    asString(body.get("displayName")),
                    asString(body.get("givenName")),
                    asString(body.get("surname")),
                    asString(body.get("mail")),
                    asString(body.get("userPrincipalName")),
                    asBoolean(body.get("accountEnabled")));
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("No se pudo consultar el usuario en Microsoft Graph: "
                    + rootMessage(e));
        }
    }

    public GraphPhoto getPhoto(String upnOrEmail, String currentETag) {
        String url = GRAPH_BASE + "/users/" + encode(upnOrEmail) + "/photo/$value";
        try {
            HttpHeaders headers = new HttpHeaders();
            String token = tokenService.getAccessToken();
            headers.setBearerAuth(token);
            if (currentETag != null && !currentETag.isBlank()) {
                headers.setIfNoneMatch(currentETag);
            }
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), byte[].class);

            if (response.getStatusCode() == HttpStatus.NOT_MODIFIED) {
                return new GraphPhoto(null, currentETag, true);
            }
            String eTag = response.getHeaders().getETag();
            return new GraphPhoto(response.getBody(), eTag, false);
        } catch (HttpClientErrorException.NotFound e) {
            // El usuario no tiene foto cargada en Microsoft 365.
            return null;
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("No se pudo obtener la foto desde Microsoft Graph: "
                    + rootMessage(e));
        }
    }

    /** Prueba de conexión: solicita un token y consulta un recurso mínimo. */
    public Map<String, Object> testConnection() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            String token = tokenService.getAccessToken();
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            restTemplate.exchange(GRAPH_BASE + "/users?$top=1", HttpMethod.GET,
                    new HttpEntity<>(headers), mapType());
            result.put("success", true);
            result.put("message", "Conexión con Microsoft Graph exitosa");
        } catch (Exception e) {
            log.warn("Prueba de conexión con Microsoft Graph falló: {}", rootMessage(e));
            result.put("success", false);
            result.put("message", "No se pudo conectar con Microsoft Graph: " + rootMessage(e));
        }
        return result;
    }

    private HttpEntity<Void> authorizedEntity(String ifNoneMatch) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenService.getAccessToken());
        if (ifNoneMatch != null && !ifNoneMatch.isBlank()) {
            headers.setIfNoneMatch(ifNoneMatch);
        }
        return new HttpEntity<>(headers);
    }

    @SuppressWarnings("unchecked")
    private org.springframework.core.ParameterizedTypeReference<Map<String, Object>> mapType() {
        return new org.springframework.core.ParameterizedTypeReference<>() {
        };
    }

    private String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private Boolean asBoolean(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        return value == null ? null : Boolean.valueOf(value.toString());
    }

    private String rootMessage(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
    }
}
