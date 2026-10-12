package com.mesaayuda.dto;
import java.util.UUID;

/**
 * Resultado compacto de un área en la búsqueda global.
 *
 * <p>Igual que {@link SearchTeamItem}: dato de referencia visible para cualquier
 * rol autenticado, sin información sensible.
 *
 * @param idArea          para abrir o referir el área
 * @param nameArea        nombre del área
 * @param descriptionArea descripción, para distinguir áreas homónimas
 */
public record SearchAreaItem(UUID idArea, String nameArea, String descriptionArea) {
}