package com.mesaayuda.dto;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.List;

import java.util.UUID;
public record AnswerResponse(
        UUID idAnswer,
        String messageAnswer,
        Boolean internalAnswer,
        String user,
        String userDisplayName,
        String userPhotoUrl,
        OffsetDateTime createdAtAnswer,
        /**
         * Si el usuario que consulta ya abrio este mensaje. Con esto el boton
         * sale como "Enviado" mientras el otro todavia no lo ha visto.
         */
        Boolean readByMe,
        /** Cuantos participantes del ticket lo han leido. */
        Integer readCount,
/** Nombres de quienes ya lo leyeron, para el detalle. */
          List<String> readBy,
          /**
           * Estado de sesión de quien escribió el mensaje.
           *
           * <p>Antes no venía y el frontend lo deducía buscando el autor en la
           * lista de agentes, en la ficha del solicitante o en el titular del
           * ticket. Con un SUPER_ADMIN o un ADMIN que escribe, none de esas
           * listas lo contiene, así que el solicitante veía "Desconectado" en su
           * mensaje aunque estuviera conectado. Ahora lo dice el backend, que es
           * quien sabe quién tiene el SSE abierto.
           */
          String userSessionState,
          /** Cuándo se editó por última vez, o null. El chat lo marca como "editado". */
          OffsetDateTime editedAtAnswer,
          /**
           * Si este mensaje estaba SIN LEER por quien consulta justo antes de
           * abrir el hilo. readByMe no sirve para esto: abrir la página lo marca
           * como leído en la misma petición. Con este dato el chat pinta el
           * separador "Mensajes no leídos" donde empieza lo nuevo.
           */
          Boolean unreadOnOpen
  ) {}
