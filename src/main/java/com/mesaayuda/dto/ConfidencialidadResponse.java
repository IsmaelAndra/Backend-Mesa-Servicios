package com.mesaayuda.dto;

import java.util.List;
import java.util.UUID;

/** Confidencialidad de un ticket y las personas autorizadas a verlo. */
public record ConfidencialidadResponse(boolean confidencial, boolean todaElArea, List<Persona> personas) {

    public record Persona(UUID id, String nombre, String correo) {
    }
}
