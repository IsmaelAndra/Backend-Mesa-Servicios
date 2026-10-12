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

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
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
        URI url = graphUri("/users/" + upnOrEmail
                + "?$select=id,displayName,givenName,surname,mail,userPrincipalName,accountEnabled");
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
        URI url = graphUri("/users/" + upnOrEmail + "/photo/$value");
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

    /**
     * Verifica que una cuenta exista como usuario habilitado en el tenant.
     * No lanza excepción; devuelve {@code userFound=false} si la cuenta no existe
     * o está deshabilitada. Si la verificación falla por permisos, {@code userFound}
     * queda ausente para no bloquear el envío.
     */
    public Map<String, Object> validateMailAccount(String upnOrEmail) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (upnOrEmail == null || upnOrEmail.isBlank()) {
            result.put("userFound", false);
            result.put("success", false);
            result.put("message", "No hay cuenta remitente configurada");
            return result;
        }
        String account = upnOrEmail.trim();
        try {
            GraphUser user = getUser(account);
            if (user == null) {
                result.put("userFound", false);
                result.put("success", false);
                result.put("message", "La cuenta remitente '" + account + "' no existe en Microsoft Graph. "
                        + "Debe ser un usuario con buzón del mismo tenant del App Registration "
                        + "(verifique el UPN/correo en Entra ID).");
                return result;
            }
            if (Boolean.FALSE.equals(user.accountEnabled())) {
                result.put("userFound", false);
                result.put("success", false);
                result.put("message", "La cuenta remitente '" + account + "' está deshabilitada en Microsoft 365.");
                return result;
            }
            String mailbox = user.mail() != null && !user.mail().isBlank()
                    ? user.mail() : user.userPrincipalName();
            result.put("userFound", true);
            result.put("success", true);
            result.put("mailbox", mailbox);
            result.put("message", "Cuenta remitente válida: "
                    + (user.displayName() != null ? user.displayName() : mailbox));
            return result;
        } catch (Exception e) {
            log.warn("No se pudo verificar la cuenta remitente {}: {}", account, rootMessage(e));
            result.put("message", "No se pudo verificar la cuenta en Microsoft Graph: " + rootMessage(e));
            return result;
        }
    }

    /** Prueba de conexión: solicita un token y consulta un recurso mínimo. */
    public Map<String, Object> testConnection() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            String token = tokenService.getAccessToken();
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            restTemplate.exchange(graphUri("/users?$top=1"), HttpMethod.GET,
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

    /**
     * Envía un correo usando la API sendMail de Microsoft Graph
     * (POST /users/{remitente}/sendMail). El éxito se determina cuando Graph
     * responde con HTTP 202 Accepted (o cualquier 2xx).
     */
    public Map<String, Object> sendMail(String fromAccount, String to, String subject, String bodyHtml) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (fromAccount == null || fromAccount.isBlank()) {
            result.put("success", false);
            result.put("message", "No hay cuenta remitente configurada para el envío de correos");
            return result;
        }
        if (to == null || to.isBlank()) {
            result.put("success", false);
            result.put("message", "El destinatario no puede estar vacío");
            return result;
        }
        URI url = graphUri("/users/" + fromAccount.trim() + "/sendMail");

        Map<String, Object> messageBody = Map.of("contentType", "HTML", "content", bodyHtml);
        Map<String, Object> recipient = Map.of("emailAddress", Map.of("address", to.trim()));
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("subject", subject);
        message.put("body", messageBody);
        message.put("toRecipients", List.of(recipient));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("message", message);
        payload.put("saveToSentItems", true);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenService.getAccessToken());
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(payload, headers), String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Email aceptado por Microsoft Graph (HTTP {}) para {}", 
                        response.getStatusCode().value(), to);
                result.put("success", true);
                result.put("status", response.getStatusCode().value());
                result.put("message", "Correo aceptado por Microsoft Graph (HTTP "
                        + response.getStatusCode().value() + ")");
            } else {
                result.put("success", false);
                result.put("message", "Microsoft devolvió HTTP " + response.getStatusCode().value());
            }
        } catch (HttpClientErrorException e) {
            int status = e.getStatusCode().value();
            log.warn("Fallo al enviar email por Graph para {}: HTTP {}", to, status);
            result.put("success", false);
            if (status == 404) {
                result.put("message", "Microsoft Graph no encontró la cuenta remitente '"
                        + fromAccount.trim() + "'. Debe ser un usuario con buzón habilitado en el mismo "
                        + "tenant del App Registration (verifique el UPN/correo en Entra ID).");
            } else if (status == 403) {
                result.put("message", "La aplicación no tiene permiso para enviar correo (Mail.Send). "
                        + "Conceda el permiso y el consentimiento de administrador en Azure AD.");
            } else if (status == 401) {
                result.put("message", "Token de Microsoft Graph inválido o expirado. "
                        + "Verifique Tenant ID / Client ID / Client Secret.");
            } else {
                result.put("message", "Error HTTP " + status + ": " + e.getResponseBodyAsString());
            }
        } catch (Exception e) {
            log.warn("No se pudo enviar el correo por Graph: {}", rootMessage(e));
            result.put("success", false);
            result.put("message", "No se pudo enviar el correo: " + rootMessage(e));
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

    /**
     * Construye la URI absoluta sin pre-codificar: RestTemplate no debe volver a
     * codificar el {@code @} del UPN (evita el doble encoding que Graph rechaza
     * como ErrorInvalidUser).
     */
    private URI graphUri(String path) {
        return URI.create(GRAPH_BASE + path);
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
