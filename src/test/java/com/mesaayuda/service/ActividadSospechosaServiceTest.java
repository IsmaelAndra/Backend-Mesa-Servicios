package com.mesaayuda.service;

import com.mesaayuda.entity.User;
import com.mesaayuda.repository.AuditLogRepository;
import com.mesaayuda.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Detección de actividad sospechosa sobre audit_log. */
@ExtendWith(MockitoExtension.class)
class ActividadSospechosaServiceTest {

    @Mock private AuditLogRepository auditLogRepository;
    @Mock private UserRepository userRepository;
    @Mock private SystemAuditService systemAuditService;
    @Mock private NotificationService notificationService;

    @InjectMocks private ActividadSospechosaService service;

    private final User superAdmin1 = new User();
    private final User superAdmin2 = new User();

    @BeforeEach
    void setUp() {
        // Sin filas por defecto: cada prueba activa solo la regla que mira.
        lenient().when(auditLogRepository.contarPorIp(anyList(), any(), anyLong())).thenReturn(List.of());
        lenient().when(auditLogRepository.contarPorUsuario(anyList(), any(), anyLong())).thenReturn(List.of());
        lenient().when(auditLogRepository.contarPorEntidad(anyList(), any(), anyLong())).thenReturn(List.of());
        lenient().when(userRepository.findByRolUserNameRoleAndStatusUserAndDeletedUserFalseOrderByCreatedAtUserAsc(
                "SUPER_ADMIN", "ACTIVE")).thenReturn(List.of(superAdmin1, superAdmin2));
    }

    @Test
    void muchosLoginsFallidosDesdeUnaIpAvisanATodosLosSuperAdmin() {
        when(auditLogRepository.contarPorIp(eq(List.of("LOGIN_FAILED")), any(), anyLong()))
                .thenReturn(List.<Object[]>of(new Object[]{"10.0.0.8", 25L}));

        List<String> alertas = service.revisar();

        assertEquals(1, alertas.size());
        assertTrue(alertas.get(0).contains("10.0.0.8"));
        verify(systemAuditService).record(eq("SUSPICIOUS_ACTIVITY"), eq("Seguridad"), isNull(),
                eq("sistema"), startsWith("clave=LOGIN_FAILED_IP|10.0.0.8;"));
        verify(notificationService).sendNotification(isNull(), eq(superAdmin1), eq("ALERTA_SEGURIDAD"),
                anyString(), anyString());
        verify(notificationService).sendNotification(isNull(), eq(superAdmin2), eq("ALERTA_SEGURIDAD"),
                anyString(), anyString());
    }

    @Test
    void barridoDeAccesosDenegadosAvisa() {
        when(auditLogRepository.contarPorUsuario(eq(List.of("ACCESS_DENIED")), any(), anyLong()))
                .thenReturn(List.<Object[]>of(new Object[]{"usuario@coac.test", 14L}));

        List<String> alertas = service.revisar();

        assertEquals(1, alertas.size());
        assertTrue(alertas.get(0).contains("usuario@coac.test"));
    }

    @Test
    void unaAlertaYaAvisadaNoSeRepite() {
        when(auditLogRepository.contarPorIp(eq(List.of("LOGIN_FAILED")), any(), anyLong()))
                .thenReturn(List.<Object[]>of(new Object[]{"10.0.0.8", 25L}));
        when(auditLogRepository.existsByActionAndDetailsStartingWithAndCreatedAtAfter(
                eq("SUSPICIOUS_ACTIVITY"), eq("clave=LOGIN_FAILED_IP|10.0.0.8;"), any())).thenReturn(true);

        assertTrue(service.revisar().isEmpty());
        verify(systemAuditService, never()).record(anyString(), anyString(), any(), anyString(), anyString());
        verify(notificationService, never()).sendNotification(any(), any(), anyString(), anyString(), anyString());
    }

    @Test
    void sinEventosNoHayAvisos() {
        assertTrue(service.revisar().isEmpty());
        verify(notificationService, times(0)).sendNotification(any(), any(), anyString(), anyString(), anyString());
    }
}
