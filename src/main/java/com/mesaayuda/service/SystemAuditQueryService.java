package com.mesaayuda.service;

import com.mesaayuda.dto.SystemAuditLogResponse;
import com.mesaayuda.entity.AuditLog;
import com.mesaayuda.repository.AuditLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Consulta de la auditoría del sistema.
 *
 * <p>La tabla {@code audit_log} llevaba tiempo llenándose sin que nadie pudiera
 * leerla: se escribía el rastro de todos los accesos denegados, los cambios de
 * contraseña y los intentos fallidos, y no había forma de mirarlo. Un registro que
 * no se puede consultar no sirve para detectar nada.
 */
@Service
public class SystemAuditQueryService {

    /**
     * Tope por página.
     *
     * <p>No es caprichoso. Esta tabla solo crece y cada fila lleva detalles de
     * texto; permitir un "trae todo" sería darle al servidor trabajo infinito a
     * partir de la primera consulta.
     */
    private static final int MAX_POR_PAGINA = 200;

    private static final int MAX_BUSQUEDA_USUARIO = 150;

    @Autowired
    private AuditLogRepository repository;

    /**
     * Filtra el rastro del sistema.
     *
     * @param pagina  índice desde 0
     * @param usuario coincidencia parcial del correo; se le añade el comodín
     * @param desde   límite inferior de fecha
     * @param hasta   límite superior de fecha
     */
    @Transactional(readOnly = true)
    public List<SystemAuditLogResponse> consultar(String accion, String usuario,
                                                  OffsetDateTime desde, OffsetDateTime hasta,
                                                  int pagina, int tamano) {
        // Se sanea lo que viene de la URL. Un tamaño negativo produciría una
        // excepción poco descriptiva desde el repositorio, y un comodín
        // descontrolado en la búsqueda es justo lo que no se quiere abrir.
        int limite = Math.max(1, Math.min(tamano, MAX_POR_PAGINA));
        int paginaSegura = Math.max(0, pagina);
        String patron = usuario == null || usuario.isBlank()
                ? null
                : ("%" + usuario.trim().toLowerCase().substring(0,
                        Math.min(usuario.trim().length(), MAX_BUSQUEDA_USUARIO)) + "%");

        return repository.buscar(
                        accion == null || accion.isBlank() ? null : accion.trim(),
                        patron,
                        desde == null ? java.time.OffsetDateTime.parse("1970-01-01T00:00:00Z") : desde,
                        hasta == null ? java.time.OffsetDateTime.parse("9999-12-31T23:59:59Z") : hasta,
                        PageRequest.of(paginaSegura, limite))
                .stream()
                .map(this::aRespuesta)
                .toList();
    }

    /** Acciones disponibles, para el desplegable de filtro. */
    @Transactional(readOnly = true)
    public List<String> acciones() {
        return repository.accionesDistintas();
    }

    private SystemAuditLogResponse aRespuesta(AuditLog a) {
        // Sin sesión detrás —una tarea programada, un arranque— el usuario queda
        // a null. Se muestra como "sistema" porque en la pantalla una celda vacía
        // se lee como un dato que falta, y aquí significa algo concreto.
        return new SystemAuditLogResponse(
                a.getIdAudit(),
                a.getUsername() == null || a.getUsername().isBlank() ? "sistema" : a.getUsername(),
                a.getAction(),
                a.getEntity(),
                a.getEntityId(),
                a.getIpAddress(),
                a.getDetails(),
                a.getHttpMethod(),
                a.getRequestUri(),
                a.getRequestId(),
                a.getCreatedAt());
    }
}