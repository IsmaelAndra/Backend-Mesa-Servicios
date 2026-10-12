package com.mesaayuda.service;

import com.mesaayuda.dto.GraphAuditResponse;
import com.mesaayuda.dto.MicrosoftGraphConfigRequest;
import com.mesaayuda.dto.MicrosoftGraphConfigResponse;
import com.mesaayuda.entity.BrandingConfig;
import com.mesaayuda.entity.GraphConfigAudit;
import com.mesaayuda.entity.MicrosoftGraphConfig;
import com.mesaayuda.repository.BrandingConfigRepository;
import com.mesaayuda.repository.GraphConfigAuditRepository;
import com.mesaayuda.repository.MicrosoftGraphConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Administra la configuración de Microsoft Graph (solo SUPER_ADMIN).
 *
 * <p>El client_secret se cifra (AES-GCM) antes de persistir; el frontend jamás
 * lo recibe. Cada modificación se registra en la tabla de auditoría sin incluir
 * el valor del secreto.</p>
 */
@Service
public class MicrosoftGraphConfigService {

    private static final String DEFAULT_SCOPE = "https://graph.microsoft.com/.default";
    private static final String DEFAULT_GRANT_TYPE = "client_credentials";
    private static final int DEFAULT_SYNC_MINUTES = 1440;

    @Autowired
    private MicrosoftGraphConfigRepository configRepository;

    @Autowired
    private GraphConfigAuditRepository auditRepository;

    @Autowired
    private SecretCryptoService cryptoService;

    @Autowired
    private MicrosoftGraphTokenService tokenService;

    @Autowired
    private MicrosoftGraphService graphService;

    @Autowired
    private BrandingConfigRepository brandingConfigRepository;

    /**
     * @Lazy rompe el ciclo: NotificationService ya usa a este servicio para enviar
     * las notificaciones por correo, y aquí se necesita a la inversa solo para la
     * plantilla del correo de prueba.
     */
    @Lazy
    @Autowired
    private NotificationService notificationService;

    @Value("${app.public-base-url:}")
    private String appBaseUrl;

    @Transactional(readOnly = true)
    public MicrosoftGraphConfigResponse getConfig() {
        return toResponse(configRepository.findFirstByOrderByIdGraphConfigAsc().orElseGet(MicrosoftGraphConfig::new));
    }

    @Transactional(readOnly = true)
    public List<GraphAuditResponse> auditList() {
        return auditRepository.findTop200ByOrderByCreatedAtDesc().stream()
                .map(a -> new GraphAuditResponse(
                        a.getIdGraphAudit(),
                        a.getChangedBy(),
                        a.getChangedByEmail(),
                        a.getAction(),
                        a.getDetails(),
                        a.getIpAddress(),
                        a.getCreatedAt()))
                .toList();
    }

    /**
     * Guarda la configuración. Si {@code clientSecret} viene vacío o con asteriscos,
     * se conserva el secreto cifrado existente (nunca se sobrescribe con null, "" o "****").
     */
    @Transactional
    public MicrosoftGraphConfigResponse save(MicrosoftGraphConfigRequest request, UUID userId, String email, String ip) {
        MicrosoftGraphConfig config = configRepository.findFirstByOrderByIdGraphConfigAsc()
                .orElseGet(MicrosoftGraphConfig::new);
        boolean isNew = config.getIdGraphConfig() == null;
        StringBuilder changes = new StringBuilder();

        config.setTenantId(field(request.tenantId(), config.getTenantId(), "tenant_id", changes));
        config.setClientId(field(request.clientId(), config.getClientId(), "client_id", changes));

        if (request.scope() != null && !request.scope().isBlank()) {
            String scope = request.scope().trim();
            if (!scope.equals(config.getScope())) {
                changes.append("scope; ");
                config.setScope(scope);
            }
        }
        if (request.grantType() != null && !request.grantType().isBlank()) {
            String grant = request.grantType().trim();
            if (!grant.equals(config.getGrantType())) {
                changes.append("grant_type; ");
                config.setGrantType(grant);
            }
        }
        if (request.secretExpiresAt() != null) {
            if (!request.secretExpiresAt().equals(config.getSecretExpiresAt())) {
                changes.append("secret_expires_at; ");
                config.setSecretExpiresAt(request.secretExpiresAt());
            }
        }
        if (request.enabled() != null && !request.enabled().equals(config.getEnabled())) {
            changes.append("enabled; ");
            config.setEnabled(request.enabled());
        }
        if (request.syncIntervalMinutes() != null && request.syncIntervalMinutes() > 0) {
            if (!request.syncIntervalMinutes().equals(config.getSyncIntervalMinutes())) {
                changes.append("sync_interval_minutes; ");
                config.setSyncIntervalMinutes(request.syncIntervalMinutes());
            }
        }
        if (request.mailEnabled() != null && !request.mailEnabled().equals(config.getMailEnabled())) {
            changes.append("mail_enabled; ");
            config.setMailEnabled(request.mailEnabled());
        }
        if (request.mailFromAccount() != null && !isBlank(request.mailFromAccount())) {
            String account = request.mailFromAccount().trim();
            if (!account.equals(config.getMailFromAccount())) {
                changes.append("mail_from_account; ");
                config.setMailFromAccount(account);
            }
        }

        // Secret: solo se reemplaza si llega un valor real y no enmascarado.
        String secret = request.clientSecret();
        if (secret != null && !secret.isBlank() && !secret.trim().matches("\\*+")) {
            config.setClientSecretEncrypted(cryptoService.encrypt(secret.trim()));
            changes.append("client_secret (actualizado); ");
        }

        config.setUpdatedBy(userId);
        MicrosoftGraphConfig saved = configRepository.save(config);

        tokenService.invalidate();

        String action = isNew ? "CREATED" : "UPDATED";
        String detail = changes.length() == 0 ? "sin cambios de valor" : changes.toString().trim();
        audit(saved.getIdGraphConfig(), userId, email, action, detail, ip);

        return toResponse(saved);
    }

    /** Prueba la conexión real contra Microsoft sin exponer secretos ni persistir tokens. */
    @Transactional(readOnly = true)
    public Map<String, Object> testConnection() {
        return graphService.testConnection();
    }

    /**
     * Envía un correo de prueba mediante Graph usando la cuenta configurada como
     * remitente. Si {@code to} viene vacío, se envía a la misma cuenta remitente.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> testEmail(String to) {
        MicrosoftGraphConfig config = configRepository.findFirstByOrderByIdGraphConfigAsc().orElse(null);
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            return Map.of("success", false, "message",
                    "Primero habilite y guarde la configuración general de Microsoft Graph");
        }
        if (!Boolean.TRUE.equals(config.getMailEnabled()) || isBlank(config.getMailFromAccount())) {
            return Map.of("success", false, "message",
                    "Active 'Enviar notificaciones por correo' y configure la cuenta remitente");
        }
        String sender = config.getMailFromAccount().trim();
        String receiver = isBlank(to) ? sender : to.trim();

        Map<String, Object> check = graphService.validateMailAccount(sender);
        if (Boolean.FALSE.equals(check.get("userFound"))) {
            return check;
        }
        if (Boolean.TRUE.equals(check.get("userFound")) && check.get("mailbox") instanceof String mailbox
                && !mailbox.isBlank()) {
            sender = mailbox;
            if (isBlank(to)) {
                receiver = sender;
            }
        }

        // El correo de prueba sale de la plantilla PRUEBA_ENVIO de la sección
        // Plantillas; si el SUPER_ADMIN la borró, se usa el armazón de respaldo.
        String[] plantilla = notificationService.plantillaPruebaEnvio(null, receiver);
        if (plantilla != null) {
            return graphService.sendMail(sender, receiver, plantilla[0], plantilla[1]);
        }

        return graphService.sendMail(sender, receiver,
                "Prueba de correo - Mesa de Servicios",
                testEmailBody());
    }

    /**
     * Cuerpo del correo de prueba con el mismo armazón del sistema: cabecera
     * azul con el logo del branding, contenido y pie institucional.
     */
    private String testEmailBody() {
        String url = brandingLogoUrl();
        String logo = url.isBlank()
                ? "<span style=\"font-family:Arial,Helvetica,sans-serif;font-size:22px;font-weight:bold;color:#ffffff;\">Mesa de Servicios</span>"
                : "<img src=\"" + url + "\" alt=\"Mesa de Servicios\" "
                        + "style=\"max-height:60px;max-width:220px;width:auto;height:auto;display:inline-block;\" />";
        return "<div style=\"background:#f3f4f6;padding:32px 16px;\">"
                + "<div style=\"max-width:620px;margin:0 auto;background:#ffffff;border-radius:12px;overflow:hidden;\">"
                + "<div style=\"background:#062e86;padding:24px 32px;text-align:center;\">" + logo + "</div>"
                + "<div style=\"padding:28px 36px 12px;font-family:Arial,Helvetica,sans-serif;color:#374151;\">"
                + "<h2 style=\"color:#062e86;margin:0 0 18px;\">Prueba de configuración de correo</h2>"
                + "<p>Este mensaje confirma que el envío de notificaciones por "
                + "<b>Microsoft Graph</b> funciona correctamente.</p>"
                + "</div>"
                + "<div style=\"background:#062e86;padding:20px 32px;text-align:center;font-family:Arial,Helvetica,sans-serif;\">"
                + "<p style=\"margin:0;color:#ffffff;font-weight:bold;font-size:14px;\">Mesa de Servicios</p>"
                + "<p style=\"margin:6px 0 0;color:#c7d2fe;font-size:12px;\">Servicio de soporte técnico · COAC Magisterio de Pichincha</p>"
                + "<p style=\"margin:6px 0 0;color:#aec0dd;font-size:11px;\">© " + Year.now().getValue()
                + " COAC Magisterio de Pichincha. Todos los derechos reservados.</p>"
                + "</div>"
                + "</div>"
                + "<p style=\"max-width:620px;margin:12px auto 0;text-align:center;font-family:Arial,Helvetica,sans-serif;font-size:11px;color:#9ca3af;\">"
                + "Este correo fue generado automáticamente; por favor no responda a este mensaje.</p>"
                + "</div>";
    }

    /**
     * URL absoluta del logo del sistema (app.public-base-url + logo_url).
     */
    private String brandingLogoUrl() {
        String path = brandingConfigRepository.findTopByOrderByIdBrandingAsc()
                .map(BrandingConfig::getLogoUrl).orElse("");
        if (path == null || path.isBlank()) return "";
        if (path.startsWith("http")) return path;
        String base = appBaseUrl == null ? "" : appBaseUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + (path.startsWith("/") ? "" : "/") + path;
    }

    private void audit(UUID configId, UUID userId, String email, String action, String details, String ip) {
        GraphConfigAudit entry = new GraphConfigAudit();
        entry.setConfigId(configId);
        entry.setChangedBy(userId);
        entry.setChangedByEmail(email);
        entry.setAction(action);
        entry.setDetails(details);
        entry.setIpAddress(ip);
        auditRepository.save(entry);
    }

    private String field(String incoming, String current, String label, StringBuilder changes) {
        if (incoming == null) {
            return current;
        }
        String value = incoming.trim();
        if (value.isBlank()) {
            return current;
        }
        if (!value.equals(current)) {
            changes.append(label).append("; ");
        }
        return value;
    }

    private MicrosoftGraphConfigResponse toResponse(MicrosoftGraphConfig config) {
        boolean hasSecret = config.getClientSecretEncrypted() != null && !config.getClientSecretEncrypted().isBlank();
        String status;
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            status = "DISABLED";
        } else if (isBlank(config.getTenantId()) || isBlank(config.getClientId()) || !hasSecret) {
            status = "INCOMPLETE";
        } else {
            status = "CONNECTED";
        }
        return new MicrosoftGraphConfigResponse(
                config.getIdGraphConfig(),
                config.getTenantId(),
                config.getClientId(),
                hasSecret,
                config.getScope() != null && !config.getScope().isBlank() ? config.getScope() : DEFAULT_SCOPE,
                config.getGrantType() != null && !config.getGrantType().isBlank() ? config.getGrantType() : DEFAULT_GRANT_TYPE,
                config.getSecretExpiresAt(),
                Boolean.TRUE.equals(config.getEnabled()),
                config.getSyncIntervalMinutes() != null && config.getSyncIntervalMinutes() > 0
                        ? config.getSyncIntervalMinutes() : DEFAULT_SYNC_MINUTES,
                Boolean.TRUE.equals(config.getMailEnabled()),
                isBlank(config.getMailFromAccount()) ? null : config.getMailFromAccount().trim(),
                config.getLastSyncAt(),
                config.getUpdatedAt(),
                status);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}