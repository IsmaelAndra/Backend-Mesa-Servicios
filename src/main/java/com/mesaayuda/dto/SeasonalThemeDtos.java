package com.mesaayuda.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** DTO de los temas de temporada. */
public final class SeasonalThemeDtos {

    private SeasonalThemeDtos() {
    }

    /** Tema que toca hoy, para el login, el navbar y el saludo del inicio. */
    public record TemaActual(String code, String name, String greeting,
                             String backgroundUrl, String logoUrl, String dashboardUrl,
                             /** Icono de la pestaña/app mientras dura el tema (null = el normal). */
                             String iconUrl) {
    }

    /** Un tema en la pantalla de administración. */
    public record Tema(UUID id, String code, String name, boolean active,
                       int startMonth, int startDay, int endMonth, int endDay,
                       String greeting, String backgroundUrl, String logoUrl, String dashboardUrl,
                       String iconUrl,
                       boolean vigenteHoy,
                       /** CARNAVAL si las fechas se calculan solas (entonces start/end son las próximas). */
                       String rule) {
    }

    public record TemaRequest(
            @NotBlank @Size(max = 80) String name,
            @NotNull Boolean active,
            @NotNull @Min(1) @Max(12) Integer startMonth,
            @NotNull @Min(1) @Max(31) Integer startDay,
            @NotNull @Min(1) @Max(12) Integer endMonth,
            @NotNull @Min(1) @Max(31) Integer endDay,
            @Size(max = 200) String greeting) {
    }

    /** Configuración general más la lista de temas. */
    public record Panel(boolean enabled, String forcedCode, TemaActual actual, List<Tema> temas) {
    }

    public record ConfigRequest(@NotNull Boolean enabled, @Size(max = 30) String forcedCode) {
    }
}
