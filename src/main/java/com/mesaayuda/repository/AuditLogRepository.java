package com.mesaayuda.repository;

import java.util.UUID;

import com.mesaayuda.entity.AuditLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    List<AuditLog> findTop200ByOrderByCreatedAtDesc();

    /**
     * Consulta filtrada de la auditoría del sistema.
     *
     * <p>Los filtros son todos opcionales y se acumulan con AND. El orden es por
     * fecha descendente porque la pregunta más habitual es "¿qué acaba de pasar?",
     * y el límite se aplica en la base y no en Java: con una tabla que solo crece,
     * cargarla entera para descartar filas en memoria sería la forma rápida de
     * tirar abajo el servidor.
     *
     * <p>La búsqueda por usuario usa coincidencia parcial y sin distinguir
     * mayúsculas, porque quien busca no recuerda si escribió el correo completo
     * o solo el prefijo.
     *
     * <p>Las fechas son obligatorias: quien llama pone límites amplios si no hay
     * filtro. Con {@code :desde IS NULL}, Hibernate envía el nulo como bytea,
     * PostgreSQL no puede compararlo con una fecha (ni con cast) y la pantalla de
     * auditoría daba un 500 cada vez que se abría sin filtro de fechas.
     */
    @Query("""
            SELECT a FROM AuditLog a
             WHERE (:accion IS NULL OR a.action = :accion)
               AND (:usuario IS NULL OR lower(coalesce(a.username, '')) LIKE :usuario)
               AND a.createdAt >= :desde
               AND a.createdAt <= :hasta
             ORDER BY a.createdAt DESC, a.idAudit DESC
            """)
    List<AuditLog> buscar(@Param("accion") String accion,
                          @Param("usuario") String usuario,
                          @Param("desde") OffsetDateTime desde,
                          @Param("hasta") OffsetDateTime hasta,
                          Pageable pageable);

    /** Acciones distintas presentes en la tabla, para alimentar el desplegable. */
    @Query("SELECT DISTINCT a.action FROM AuditLog a WHERE a.action IS NOT NULL ORDER BY a.action")
    List<String> accionesDistintas();

    // ===================== DETECCIÓN DE ACTIVIDAD SOSPECHOSA =====================

    /** IPs con al menos {@code minimo} eventos de esas acciones desde {@code desde}. */
    @Query("""
            SELECT a.ipAddress, COUNT(a) FROM AuditLog a
             WHERE a.action IN :acciones AND a.createdAt >= :desde AND a.ipAddress IS NOT NULL
             GROUP BY a.ipAddress
            HAVING COUNT(a) >= :minimo
            """)
    List<Object[]> contarPorIp(@Param("acciones") List<String> acciones,
                               @Param("desde") OffsetDateTime desde,
                               @Param("minimo") long minimo);

    /** Usuarios con al menos {@code minimo} eventos de esas acciones desde {@code desde}. */
    @Query("""
            SELECT a.username, COUNT(a) FROM AuditLog a
             WHERE a.action IN :acciones AND a.createdAt >= :desde AND a.username IS NOT NULL
             GROUP BY a.username
            HAVING COUNT(a) >= :minimo
            """)
    List<Object[]> contarPorUsuario(@Param("acciones") List<String> acciones,
                                    @Param("desde") OffsetDateTime desde,
                                    @Param("minimo") long minimo);

    /** Entidades (p. ej. id de usuario) con eventos de esas acciones desde {@code desde}. */
    @Query("""
            SELECT a.entityId, COUNT(a) FROM AuditLog a
             WHERE a.action IN :acciones AND a.createdAt >= :desde AND a.entityId IS NOT NULL
             GROUP BY a.entityId
            HAVING COUNT(a) >= :minimo
            """)
    List<Object[]> contarPorEntidad(@Param("acciones") List<String> acciones,
                                    @Param("desde") OffsetDateTime desde,
                                    @Param("minimo") long minimo);

    /** Si ya se avisó de esta misma alerta hace poco, para no repetirla en cada vuelta. */
    boolean existsByActionAndDetailsStartingWithAndCreatedAtAfter(String action, String prefijo,
                                                                  OffsetDateTime desde);
}
