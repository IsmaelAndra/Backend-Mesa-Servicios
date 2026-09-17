package com.mesaayuda.service;

import com.mesaayuda.entity.SmtpConfig;
import com.mesaayuda.repository.SmtpConfigRepository;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Properties;

@Service
public class SmtpService {

    private static final Logger log = LoggerFactory.getLogger(SmtpService.class);

    @Autowired
    private SmtpConfigRepository smtpConfigRepository;

    @Autowired
    private JavaMailSender fallbackMailSender;

    @Value("${spring.mail.username:}")
    private String propertyUsername;

    @Value("${microsoft.graph.from-address:}")
    private String propertyFrom;

    // ============================ CONFIGURACIÓN ============================

    @Transactional(readOnly = true)
    public SmtpConfig getConfig() {
        return smtpConfigRepository.findFirstByOrderByIdSmtpAsc().orElse(defaultConfig());
    }

    @Transactional
    public SmtpConfig save(SmtpConfig incoming) {
        SmtpConfig current = smtpConfigRepository.findFirstByOrderByIdSmtpAsc().orElse(null);
        SmtpConfig config = current != null ? current : new SmtpConfig();
        if (incoming.getHostSmtp() != null && !incoming.getHostSmtp().isBlank()) config.setHostSmtp(incoming.getHostSmtp().trim());
        if (incoming.getPortSmtp() != null) config.setPortSmtp(incoming.getPortSmtp());
        if (incoming.getUsernameSmtp() != null) config.setUsernameSmtp(incoming.getUsernameSmtp().trim());
        if (incoming.getPasswordSmtp() != null) config.setPasswordSmtp(incoming.getPasswordSmtp());
        if (incoming.getFromAddress() != null) config.setFromAddress(incoming.getFromAddress().trim());
        if (incoming.getDisplayName() != null) config.setDisplayName(incoming.getDisplayName().trim());
        if (incoming.getUseTls() != null) config.setUseTls(incoming.getUseTls());
        if (incoming.getEnabledSmtp() != null) config.setEnabledSmtp(incoming.getEnabledSmtp());
        return smtpConfigRepository.save(config);
    }

    // ============================ ENVÍO ============================

    public JavaMailSender buildSender() {
        SmtpConfig config = getConfig();
        if (!Boolean.TRUE.equals(config.getEnabledSmtp())
                || config.getHostSmtp() == null || config.getHostSmtp().isBlank()) {
            return fallbackMailSender;
        }
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(config.getHostSmtp());
        sender.setPort(config.getPortSmtp() != null ? config.getPortSmtp() : 587);
        sender.setUsername(config.getUsernameSmtp());
        sender.setPassword(config.getPasswordSmtp());

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", String.valueOf(Boolean.TRUE.equals(config.getUseTls())));
        props.put("mail.smtp.ssl.trust", "*");
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");
        return sender;
    }

    public String fromAddress() {
        SmtpConfig config = getConfig();
        if (config.getFromAddress() != null && !config.getFromAddress().isBlank()) return config.getFromAddress();
        if (propertyFrom != null && !propertyFrom.isBlank()) return propertyFrom;
        if (config.getUsernameSmtp() != null && !config.getUsernameSmtp().isBlank()) return config.getUsernameSmtp();
        if (propertyUsername != null && !propertyUsername.isBlank()) return propertyUsername;
        return "no-reply@coac.local";
    }

    public String displayName() {
        SmtpConfig config = getConfig();
        return config.getDisplayName() != null ? config.getDisplayName() : "";
    }

    /**
     * Envía un correo de prueba usando parámetros explícitos (formulario sin guardar
     * o configuración persistida). Devuelve el resultado para mostrarlo en la UI.
     */
    public Map<String, Object> testSend(String to, String host, int port, String username,
                                        String password, String from, boolean useTls) {
        try {
            JavaMailSenderImpl sender = new JavaMailSenderImpl();
            sender.setHost(host);
            sender.setPort(port);
            sender.setUsername(username);
            sender.setPassword(password);
            Properties props = sender.getJavaMailProperties();
            props.put("mail.transport.protocol", "smtp");
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", String.valueOf(useTls));
            props.put("mail.smtp.ssl.trust", "*");
            props.put("mail.smtp.connectiontimeout", "8000");
            props.put("mail.smtp.timeout", "15000");
            props.put("mail.smtp.writetimeout", "15000");

            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            String fromAddress = (from != null && !from.isBlank()) ? from
                    : (username != null && !username.isBlank()) ? username : "no-reply@coac.local";
            helper.setFrom(new InternetAddress(fromAddress, "Mesa de Ayuda COAC", "UTF-8"));
            helper.setTo(to);
            helper.setSubject("Prueba de configuración SMTP");
            helper.setText("<div style='font-family:Arial,Helvetica,sans-serif;color:#333'>"
                    + "<h2 style='color:#062e86'>Mesa de Ayuda COAC</h2>"
                    + "<p>Este es un correo de prueba. Si lo está leyendo, la configuración SMTP es correcta.</p>"
                    + "<p style='color:#999;font-size:12px'>Enviado desde el panel de administración.</p></div>", true);
            sender.send(message);
            log.info("Correo de prueba SMTP enviado a {}", to);
            return Map.of("success", true, "message", "Correo de prueba enviado a " + to);
        } catch (Exception e) {
            log.error("Falló el correo de prueba SMTP: {}", e.getMessage());
            return Map.of("success", false, "message", "Error: " + e.getMessage());
        }
    }

    private SmtpConfig defaultConfig() {
        SmtpConfig config = new SmtpConfig();
        config.setHostSmtp("smtp.office365.com");
        config.setPortSmtp(587);
        config.setUseTls(true);
        config.setEnabledSmtp(true);
        return config;
    }
}