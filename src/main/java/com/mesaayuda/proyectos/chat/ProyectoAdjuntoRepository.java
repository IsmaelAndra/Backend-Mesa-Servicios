package com.mesaayuda.proyectos.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ProyectoAdjuntoRepository extends JpaRepository<ProyectoAdjunto, UUID> {
    List<ProyectoAdjunto> findByMensajeIdIn(Collection<UUID> mensajes);
}
