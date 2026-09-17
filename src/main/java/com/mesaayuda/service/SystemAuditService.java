package com.mesaayuda.service;

import com.mesaayuda.entity.AuditLog;
import com.mesaayuda.repository.AuditLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class SystemAuditService {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Transactional
    public void record(String action, String entity, Long entityId) {
        record(action, entity, entityId, currentUsername());
    }

    @Transactional
    public void record(String action, String entity, Long entityId, String username) {
        AuditLog log = new AuditLog();
        log.setAction(action);
        log.setEntity(entity);
        log.setEntityId(entityId);
        log.setUsername(username);
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                log.setIpAddress(attrs.getRequest().getRemoteAddr());
            }
        } catch (Exception ignored) {
            // contexto ausente (schedulers)
        }
        auditLogRepository.save(log);
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof UserDetails details)) {
            return null;
        }
        return details.getUsername();
    }
}