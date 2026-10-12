package com.mesaayuda.dto;
import java.util.UUID;

/**
 * Agente disponible para asignar un ticket o una actividad.
 *
 * <p>Existe porque este listado lo consume el kanban del personal, que necesita
 * un identificador, un nombre y saber quien esta en linea. Antes se servia el
 * {@code UserResponse} completo, con lo que cualquier usuario con sesin se
 * llevaba por esta via la identificacion, el telefono, la foto, el UPN de
 * Microsoft 365 y el estado de sincronizacion de todo el personal. Esos datos se
 * quedan en {@code GET /users/{id}}, que si exige administrador.
 *
 * <p>El endpoint queda ademas restringido al personal: ningun solicitante
 * necesita la lista de agentes, porque al reportar no elige agente.
 *
 * @param idUser      identificador, para el valor del desplegable
 * @param displayName nombre mostrado, con el que la persona se reconoce
 * @param corporateEmailUser correo corporativo. Mismo nombre de campo que en User, para que el
 *                           emparejamiento que ya hace el frontend siga funcionando.
 * @param sessionState EN_LINEA, RECIENTE o DESCONECTADO. Lo pinta el kanban para
 *                    saber quien esta trabajando ahora. Viene de la actividad de
 *                    renovacion de token y de las conexiones SSE abiertas, no de
 *                    ningun dato almacenado del usuario.
 */
public record AgentOptionResponse(UUID idUser, String displayName, String corporateEmailUser,
        String photoUrl, String sessionState) {
}