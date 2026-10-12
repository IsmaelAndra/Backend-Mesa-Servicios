package com.mesaayuda.service;

import com.mesaayuda.dto.RoutingConfigRequest;
import com.mesaayuda.entity.IdsConfiguracion;
import com.mesaayuda.entity.RoutingConfig;
import com.mesaayuda.repository.RoutingConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Configuración global de asignación automática (fila única).
 * La consume RoutingService para decidir si un ticket se asigna al crearse.
 */
@Service
public class RoutingConfigService {

    /** Id fijo de la fila unica: system_config de routing. */
    private static final UUID SINGLETON_ID = IdsConfiguracion.ROUTING;

    @Autowired
    private RoutingConfigRepository routingConfigRepository;

    /** Devuelve la configuración; si la fila singleton no existe, la crea con los valores por defecto. */
    @Transactional
    public RoutingConfig getConfig() {
        return routingConfigRepository.findById(SINGLETON_ID).orElseGet(() -> {
            RoutingConfig config = new RoutingConfig();
            config.setId(SINGLETON_ID);
            config.setAutoAssignEnabled(true);
            config.setNotifyUnassigned(false);
            config.setUpdatedAtRoutingConfig(OffsetDateTime.now());
            return routingConfigRepository.save(config);
        });
    }

    @Transactional(readOnly = true)
    public boolean isAutoAssignEnabled() {
        return routingConfigRepository.findById(SINGLETON_ID)
                .map(c -> !Boolean.FALSE.equals(c.getAutoAssignEnabled()))
                .orElse(true);
    }

    @Transactional
    public RoutingConfig update(RoutingConfigRequest request) {
        RoutingConfig config = getConfig();
        config.setAutoAssignEnabled(!Boolean.FALSE.equals(request.autoAssignEnabled()));
        config.setNotifyUnassigned(Boolean.TRUE.equals(request.notifyUnassigned()));
        return routingConfigRepository.save(config);
    }
}
