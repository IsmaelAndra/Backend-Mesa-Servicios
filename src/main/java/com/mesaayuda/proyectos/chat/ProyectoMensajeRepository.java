package com.mesaayuda.proyectos.chat;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ProyectoMensajeRepository extends JpaRepository<ProyectoMensaje, UUID> {

    /** Los más recientes primero; el cliente los da vuelta. Nunca por id (son uuid). */
    @Query("select m from ProyectoMensaje m where m.proyectoId = :p and m.deleted = false "
            + "and (cast(:antes as OffsetDateTime) is null or m.createdAt < :antes) "
            + "order by m.createdAt desc, m.idMensaje desc")
    List<ProyectoMensaje> pagina(@Param("p") UUID proyectoId, @Param("antes") OffsetDateTime antesDe, Pageable pagina);

    /** Mensajes de otros posteriores a la última lectura, por proyecto: [proyectoId, cantidad]. */
    @Query(value = "select m.proyecto_proyecto_mensaje, count(*) from proyecto_mensaje m "
            + "left join proyecto_lectura l on l.proyecto_proyecto_lectura = m.proyecto_proyecto_mensaje "
            + "and l.usuario_proyecto_lectura = :u "
            + "where m.proyecto_proyecto_mensaje in (:ps) and m.deleted_proyecto_mensaje = false "
            + "and m.autor_proyecto_mensaje <> :u "
            + "and (l.leido_at_proyecto_lectura is null or m.created_at_proyecto_mensaje > l.leido_at_proyecto_lectura) "
            + "group by m.proyecto_proyecto_mensaje", nativeQuery = true)
    List<Object[]> noLeidos(@Param("u") UUID usuario, @Param("ps") Collection<UUID> proyectos);

    /** Guarda hasta cuándo leyó (tabla proyecto_lectura, sin entidad propia). */
    @Modifying
    @Query(value = "insert into proyecto_lectura (proyecto_proyecto_lectura, usuario_proyecto_lectura, leido_at_proyecto_lectura) "
            + "values (:p, :u, now()) on conflict (proyecto_proyecto_lectura, usuario_proyecto_lectura) "
            + "do update set leido_at_proyecto_lectura = now()", nativeQuery = true)
    void marcarLeido(@Param("p") UUID proyecto, @Param("u") UUID usuario);
}
