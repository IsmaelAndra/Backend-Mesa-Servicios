package com.mesaayuda.dto;
import java.util.UUID;

/**
 * Resultado compacto de un equipo de soporte en la búsqueda global.
 *
 * <p>Los equipos y las áreas son dato de referencia, igual que el catálogo que
 * consume el reporte de tickets: cualquier rol con sesión puede verlos. Un
 * solicitante no puede usar el resultado para nada sensible, así que se sirve
 * el mismo mínimo que devuelven los desplegables.
 *
 * @param idTeam          para abrir o referir el equipo
 * @param nameTeam        nombre del equipo
 * @param descriptionTeam descripción, para distinguir equipos homónimos
 */
public record SearchTeamItem(UUID idTeam, String nameTeam, String descriptionTeam) {
}